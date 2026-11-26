package com.arrheniq.masterdata;

import com.arrheniq.kernel.esign.ApprovalService;
import com.arrheniq.kernel.esign.Signature;

import java.util.List;

/**
 * A product with its signature manifestation (URS-ESG-001) and where its approval stands
 * (e.g. 1 of 2 signatures with {@code approval.product = DOUBLE}).
 */
public record ProductDetails(Product product, List<Signature> signatures, ApprovalService.Progress approval) {
}
