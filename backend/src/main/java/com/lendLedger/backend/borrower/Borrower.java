package com.lendLedger.backend.borrower;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "borrower")
public class Borrower {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Column(name = "phone_normalized", nullable = false)
    private String phoneNormalized;

    private String email;

    @Column(name = "national_id")
    private String nationalId;

    /** Nothing in the system verifies an ID number yet, so this stays NOT_VERIFIED. */
    @Column(name = "kyc_status", nullable = false)
    private String kycStatus;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BorrowerStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Borrower() { }

    static Borrower create(UUID tenantId, UUID createdBy, String fullName, String phone, String phoneNormalized,
                           String email, String nationalId, String note) {
        Borrower b = new Borrower();
        b.tenantId = tenantId;
        b.createdBy = createdBy;
        b.fullName = fullName;
        b.phone = phone;
        b.phoneNormalized = phoneNormalized;
        b.email = email;
        b.nationalId = nationalId;
        b.kycStatus = "NOT_VERIFIED";
        b.note = note;
        b.status = BorrowerStatus.ACTIVE;
        b.createdAt = Instant.now();
        b.updatedAt = b.createdAt;
        return b;
    }

    /** Applies the new values and returns the names of the fields that actually changed. */
    List<String> applyChanges(String newName, String newPhone, String newPhoneNormalized, String newEmail,
                              String newNationalId, String newNote) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(fullName, newName)) { fullName = newName; changed.add("fullName"); }
        if (!Objects.equals(phoneNormalized, newPhoneNormalized)) { changed.add("phone"); }
        phone = newPhone;
        phoneNormalized = newPhoneNormalized;
        if (!Objects.equals(email, newEmail)) { email = newEmail; changed.add("email"); }
        if (!Objects.equals(nationalId, newNationalId)) {
            nationalId = newNationalId;
            kycStatus = "NOT_VERIFIED";
            changed.add("nationalId");
        }
        if (!Objects.equals(note, newNote)) { note = newNote; changed.add("note"); }
        if (!changed.isEmpty()) updatedAt = Instant.now();
        return changed;
    }

    void archive() { status = BorrowerStatus.ARCHIVED; updatedAt = Instant.now(); }

    void restore() { status = BorrowerStatus.ACTIVE; updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getPhoneNormalized() { return phoneNormalized; }
    public String getEmail() { return email; }
    public String getNationalId() { return nationalId; }
    public String getKycStatus() { return kycStatus; }
    public String getNote() { return note; }
    public BorrowerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}