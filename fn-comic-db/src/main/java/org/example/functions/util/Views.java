package org.example.functions.util;

/** Jackson serialization view markers for controlling field visibility by caller role. */
public class Views {
    /** Fields visible to admins only (e.g. pricePaid). */
    public interface Admin {}
    /** Marker for non-admin (public) view — excludes Admin-only fields. */
    public interface Public {}
}
