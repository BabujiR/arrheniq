package com.arrheniq.masterdata;

import com.arrheniq.kernel.esign.Signature;

import java.util.List;

/** A product with its signature manifestation (URS-ESG-001). */
public record ProductDetails(Product product, List<Signature> signatures) {
}
