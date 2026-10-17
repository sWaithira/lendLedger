package com.lendLedger.backend.loan;

/** How the money arrived. MPESA_MANUAL is a typed-in reference, not verified settlement. */
public enum PaymentMethod { MANUAL, CASH, MPESA_MANUAL, BANK }