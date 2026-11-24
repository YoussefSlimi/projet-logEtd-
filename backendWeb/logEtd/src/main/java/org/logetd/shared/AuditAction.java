package org.logetd.shared;

/** Actions transverses consignées dans le journal d'audit. */
public enum AuditAction {
    MESSAGE_SENT,
    REPORT_CREATED,
    LISTING_SUSPENDED_BY_REPORT,
    LISTING_VALIDATED,
    LISTING_REFUSED
}
