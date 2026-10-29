package com.lendLedger.backend.borrower;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.lendLedger.backend.audit.AuditService;
import com.lendLedger.backend.common.ApiException;
import com.lendLedger.backend.loan.LoanDtos;
import com.lendLedger.backend.loan.LoanService;
import com.lendLedger.backend.security.CurrentUser;
import com.lendLedger.backend.security.LendLedgerPrincipal;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports borrowers and loans from the LendLedger Excel template. Preview validates every row and saves nothing.
 * Confirm re-validates and creates everything in one transaction through the same services used for manual entry,
 * so tenant isolation, rule versions, charges and audit all apply.
 */
@Service
public class ImportService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT));
    private static final List<String[]> BORROWER_REQUIRED =
            List.of(new String[] {"fullname", "Full name"}, new String[] {"phone", "Phone"});
    private static final List<String[]> LOAN_REQUIRED = List.of(
            new String[] {"borrowerphone", "Borrower phone"}, new String[] {"amount", "Amount"},
            new String[] {"issuedate", "Issue date"}, new String[] {"termmonths", "Term (months)"});

    private record RawRow(int number, Map<String, String> cells) {
        String get(String key) {
            return cells.getOrDefault(key, "");
        }
    }

    private record Sheets(List<RawRow> borrowers, List<RawRow> loans) { }

    private record NewBorrower(int row, BorrowerDtos.CreateRequest request, String phoneNorm) { }

    private record NewLoan(int row, String phoneNorm, UUID productId, BigDecimal amount, LocalDate date, int term) { }

    private static final class Plan {
        final List<ImportDtos.RowResult> rows = new ArrayList<>();
        final List<NewBorrower> newBorrowers = new ArrayList<>();
        final List<NewLoan> newLoans = new ArrayList<>();
        final Map<String, Integer> fileBorrowerRow = new HashMap<>();
        final Map<String, UUID> existingIds = new HashMap<>();
        final Set<String> archived = new HashSet<>();
        final Set<String> looked = new HashSet<>();

        void add(String sheet, int row, String label, String status, String message) {
            rows.add(new ImportDtos.RowResult(sheet, row, label, status, message));
        }
    }

    private final BorrowerService borrowerService;
    private final BorrowerRepository borrowers;
    private final LoanService loanService;
    private final ImportRepository repo;
    private final AuditService audit;
    private final int maxRows;

    public ImportService(BorrowerService borrowerService, BorrowerRepository borrowers, LoanService loanService,
                         ImportRepository repo, AuditService audit,
                         @Value("${lendledger.import.max-rows:2000}") int maxRows) {
        this.borrowerService = borrowerService;
        this.borrowers = borrowers;
        this.loanService = loanService;
        this.repo = repo;
        this.audit = audit;
        this.maxRows = maxRows;
    }

    // ------------------------------------------------------------------ preview and confirm

    /** Checks every row and saves nothing. */
    @Transactional(readOnly = true)
    public ImportDtos.Preview preview(MultipartFile file) {
        UUID tenantId = CurrentUser.tenantId();
        byte[] bytes = bytesOf(file);
        Plan plan = plan(bytes, tenantId);
        return new ImportDtos.Preview(fileName(file), repo.batchExists(tenantId, sha256(bytes)),
                summarize(plan.rows), plan.rows);
    }

    /** Re-checks the file and creates the valid rows. Problem rows are skipped and reported. */
    @Transactional
    public ImportDtos.Report confirm(MultipartFile file) {
        LendLedgerPrincipal user = CurrentUser.require();
        UUID tenantId = user.tenantId();
        byte[] bytes = bytesOf(file);
        String hash = sha256(bytes);
        if (repo.batchExists(tenantId, hash)) throw alreadyImported();

        Plan plan = plan(bytes, tenantId);
        if (plan.newBorrowers.isEmpty() && plan.newLoans.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "nothing_to_import", "There is nothing new to import from this file");
        }

        Map<String, UUID> ids = new HashMap<>(plan.existingIds);
        for (NewBorrower nb : plan.newBorrowers) {
            ids.put(nb.phoneNorm(), borrowerService.create(nb.request()).id());
        }
        for (NewLoan nl : plan.newLoans) {
            loanService.create(new LoanDtos.CreateRequest(ids.get(nl.phoneNorm()), nl.productId(), nl.amount(),
                    nl.date(), nl.term()));
        }

        UUID batchId = UUID.randomUUID();
        try {
            repo.insertBatch(batchId, tenantId, hash, fileName(file), plan.newBorrowers.size(), plan.newLoans.size(), user.userId());
        } catch (DuplicateKeyException e) {
            throw alreadyImported();
        }
        audit.record("IMPORT_COMPLETED", "IMPORT", batchId, null, Map.of(
                "borrowers", String.valueOf(plan.newBorrowers.size()),
                "loans", String.valueOf(plan.newLoans.size())));

        List<ImportDtos.RowResult> rows = plan.rows.stream()
                .map(r -> "NEW".equals(r.status())
                        ? new ImportDtos.RowResult(r.sheet(), r.row(), r.label(), "CREATED", "Added")
                        : r)
                .toList();
        return new ImportDtos.Report(summarize(rows), rows);
    }

    // ------------------------------------------------------------------ validation

    private Plan plan(byte[] bytes, UUID tenantId) {
        Sheets sheets = readSheets(bytes);
        Plan plan = new Plan();
        planBorrowers(plan, sheets.borrowers(), tenantId);
        planLoans(plan, sheets.loans(), tenantId);
        return plan;
    }

    private void planBorrowers(Plan plan, List<RawRow> rows, UUID tenantId) {
        for (RawRow r : rows) {
            String name = r.get("fullname").trim();
            String phone = fixPhone(r.get("phone"));
            String email = r.get("email").trim();
            String nationalId = r.get("idnumber").trim();
            String note = r.get("note").trim();
            String label = name.isEmpty() ? phone : name;

            List<String> problems = new ArrayList<>();
            if (name.isEmpty()) problems.add("Full name is required");
            else if (name.length() > 200) problems.add("Name is too long");
            String norm = normalizeOrNull(phone, problems);
            if (!email.isEmpty() && (email.length() > 200 || !EMAIL.matcher(email).matches())) problems.add("Email is not valid");
            if (nationalId.length() > 40) problems.add("ID number is too long");
            if (note.length() > 1000) problems.add("Note is too long");
            if (!problems.isEmpty()) {
                plan.add("Borrowers", r.number(), label, "ERROR", String.join(". ", problems));
                continue;
            }

            Integer first = plan.fileBorrowerRow.putIfAbsent(norm, r.number());
            if (first != null) {
                plan.add("Borrowers", r.number(), label, "SKIPPED", "Same phone number as row " + first);
                continue;
            }
            UUID existing = lookupExisting(plan, tenantId, norm);
            if (existing != null) {
                plan.add("Borrowers", r.number(), label, "EXISTING",
                        plan.archived.contains(norm) ? "Already a borrower (archived)" : "Already one of your borrowers");
            } else {
                plan.newBorrowers.add(new NewBorrower(r.number(), new BorrowerDtos.CreateRequest(name, phone,
                        blankToNull(email), blankToNull(nationalId), blankToNull(note), null), norm));
                plan.add("Borrowers", r.number(), label, "NEW", "Will be added");
            }
        }
    }

    private void planLoans(Plan plan, List<RawRow> rows, UUID tenantId) {
        if (rows.isEmpty()) return;
        List<LoanDtos.ProductView> products = loanService.products();
        Map<String, UUID> byName = new HashMap<>();
        for (LoanDtos.ProductView p : products) byName.put(p.name().trim().toLowerCase(Locale.ROOT), p.id());
        Map<String, Integer> seenLoans = new HashMap<>();

        for (RawRow r : rows) {
            String phone = fixPhone(r.get("borrowerphone"));
            String label = phone + " · " + r.get("amount").trim();
            List<String> problems = new ArrayList<>();

            String norm = normalizeOrNull(phone, problems);
            if (norm != null) {
                UUID existing = lookupExisting(plan, tenantId, norm);
                boolean inFile = plan.fileBorrowerRow.containsKey(norm);
                if (!inFile && existing == null) {
                    problems.add("No borrower with this phone number. Add them on the Borrowers sheet");
                } else if (plan.archived.contains(norm)) {
                    problems.add("This borrower is archived. Restore them first");
                }
            }

            BigDecimal amount = parseAmount(r.get("amount"));
            if (amount == null || amount.signum() <= 0) problems.add("Amount must be a positive number");
            else if (amount.stripTrailingZeros().scale() > 2) problems.add("Amount can have at most 2 decimal places");

            LocalDate date = parseDate(r.get("issuedate"));
            if (date == null) problems.add("Issue date is missing or not a valid date (use 2026-10-01)");

            Integer term = parseTerm(r.get("termmonths"));
            if (term == null) problems.add("Term must be a whole number of months between 1 and 60");

            UUID productId = null;
            String productName = r.get("product").trim();
            if (productName.isEmpty()) {
                if (products.size() == 1) productId = products.get(0).id();
                else if (products.isEmpty()) problems.add("You have no product to lend from. Add one in Settings");
                else problems.add("Product is required because you offer more than one");
            } else {
                productId = byName.get(productName.toLowerCase(Locale.ROOT));
                if (productId == null) problems.add("No product named '" + productName + "'");
            }

            if (problems.isEmpty()) {
                BigDecimal money = amount.setScale(2);
                try {
                    loanService.preview(new LoanDtos.PreviewRequest(productId, money, date, term));
                } catch (ApiException e) {
                    problems.add(e.getMessage());
                }
            }
            if (!problems.isEmpty()) {
                plan.add("Loans", r.number(), label, "ERROR", String.join(". ", problems));
                continue;
            }

            BigDecimal money = amount.setScale(2);
            String key = norm + "|" + money.toPlainString() + "|" + date;
            Integer first = seenLoans.putIfAbsent(key, r.number());
            if (first != null) {
                plan.add("Loans", r.number(), label, "SKIPPED", "Same loan as row " + first + " in this file");
            } else if (repo.loanExists(tenantId, norm, money, date)) {
                plan.add("Loans", r.number(), label, "SKIPPED", "This loan is already recorded");
            } else {
                plan.newLoans.add(new NewLoan(r.number(), norm, productId, money, date, term));
                plan.add("Loans", r.number(), label, "NEW", "Will be added");
            }
        }
    }

    /** Looks a phone number up once per file. Prefers an active borrower; remembers if only an archived one exists. */
    private UUID lookupExisting(Plan plan, UUID tenantId, String norm) {
        if (plan.looked.add(norm)) {
            List<Borrower> found = borrowers.findByTenantIdAndPhoneNormalized(tenantId, norm);
            Borrower chosen = null;
            for (Borrower b : found) {
                if (b.getStatus() != BorrowerStatus.ARCHIVED) {
                    chosen = b;
                    break;
                }
            }
            if (chosen == null && !found.isEmpty()) {
                chosen = found.get(0);
                plan.archived.add(norm);
            }
            if (chosen != null) plan.existingIds.put(norm, chosen.getId());
        }
        return plan.existingIds.get(norm);
    }

    // ------------------------------------------------------------------ reading the workbook

    private Sheets readSheets(byte[] bytes) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter fmt = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            List<RawRow> borrowerRows = readSheet(wb, "Borrowers", BORROWER_REQUIRED, fmt, ev);
            List<RawRow> loanRows = readSheet(wb, "Loans", LOAN_REQUIRED, fmt, ev);
            if (borrowerRows == null && loanRows == null) {
                throw invalidFile("The file needs a sheet named Borrowers or Loans. Use the LendLedger template.");
            }
            List<RawRow> b = borrowerRows == null ? List.of() : borrowerRows;
            List<RawRow> l = loanRows == null ? List.of() : loanRows;
            if (b.size() + l.size() > maxRows) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "too_many_rows",
                        "This file has " + (b.size() + l.size()) + " rows. The most you can import at once is " + maxRows
                                + ". Split it into smaller files.");
            }
            return new Sheets(b, l);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw invalidFile("That file could not be read. Use the LendLedger template (.xlsx).");
        }
    }

    /** Returns null when the sheet does not exist. Columns are found by header name, so their order does not matter. */
    private List<RawRow> readSheet(Workbook wb, String name, List<String[]> required, DataFormatter fmt, FormulaEvaluator ev) {
        Sheet sheet = wb.getSheet(name);
        if (sheet == null) return null;

        Map<Integer, String> columns = new LinkedHashMap<>();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header != null) {
            for (Cell c : header) {
                String key = key(fmt.formatCellValue(c, ev));
                if (!key.isEmpty()) columns.put(c.getColumnIndex(), key);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String[] req : required) {
            if (!columns.containsValue(req[0])) missing.add(req[1]);
        }
        if (!missing.isEmpty()) {
            throw invalidFile("The " + name + " sheet is missing these columns: " + String.join(", ", missing)
                    + ". Use the LendLedger template.");
        }

        List<RawRow> out = new ArrayList<>();
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, String> cells = new HashMap<>();
            boolean any = false;
            for (Map.Entry<Integer, String> e : columns.entrySet()) {
                String value = cellText(row.getCell(e.getKey()), fmt, ev);
                if (!value.isEmpty()) any = true;
                cells.put(e.getValue(), value);
            }
            if (any) out.add(new RawRow(r + 1, cells));   // r + 1 = the row number the lender sees in Excel
        }
        return out;
    }

    private static String cellText(Cell cell, DataFormatter fmt, FormulaEvaluator ev) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return fmt.formatCellValue(cell, ev).trim();
    }

    private static String key(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------ small parsers

    /** Excel drops the leading zero of 0712345678 when a cell is numeric; put it back for 9-digit mobile numbers. */
    private static String fixPhone(String raw) {
        String t = raw == null ? "" : raw.trim();
        String digits = t.replaceAll("\\D", "");
        if (t.equals(digits) && digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("1"))) {
            return "0" + digits;
        }
        return t;
    }

    private static String normalizeOrNull(String phone, List<String> problems) {
        if (phone.isBlank()) {
            problems.add("Phone is required");
            return null;
        }
        try {
            return PhoneNumbers.normalize(phone);
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            return null;
        }
    }

    private static BigDecimal parseAmount(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseTerm(String raw) {
        BigDecimal value = parseAmount(raw);
        if (value == null || value.stripTrailingZeros().scale() > 0) return null;
        try {
            int months = value.intValueExact();
            return (months >= 1 && months <= 60) ? months : null;
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ignored) {
            // not ISO: try the day-first formats below
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ------------------------------------------------------------------ file helpers

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalidFile("Choose a file first");
        if (!fileName(file).toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw invalidFile("Use an Excel .xlsx file. Download the template to start.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw invalidFile("That file could not be read. Try again.");
        }
    }

    private static String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) return "upload.xlsx";
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalidFile(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_file", message);
    }

    private static ApiException alreadyImported() {
        return new ApiException(HttpStatus.CONFLICT, "already_imported", "This exact file has already been imported");
    }

    private static ImportDtos.Summary summarize(List<ImportDtos.RowResult> rows) {
        int bNew = 0, bExisting = 0, bSkipped = 0, lNew = 0, lSkipped = 0, errors = 0;
        for (ImportDtos.RowResult r : rows) {
            String s = r.status();
            boolean isNew = "NEW".equals(s) || "CREATED".equals(s);
            if ("ERROR".equals(s)) {
                errors++;
            } else if ("Borrowers".equals(r.sheet())) {
                if (isNew) bNew++;
                else if ("EXISTING".equals(s)) bExisting++;
                else bSkipped++;
            } else {
                if (isNew) lNew++;
                else lSkipped++;
            }
        }
        return new ImportDtos.Summary(bNew, bExisting, bSkipped, lNew, lSkipped, errors);
    }
}