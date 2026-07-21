/**
 * Marker package for distributed locking adapters.
 * <p>
 * Feature 2 places Oracle row claiming in {@code scheduler.engine}
 * ({@code FOR UPDATE SKIP LOCKED}). Redis or other locks can live here later
 * without changing the engine contracts.
 */
package com.laconic.scheduler.lock;
