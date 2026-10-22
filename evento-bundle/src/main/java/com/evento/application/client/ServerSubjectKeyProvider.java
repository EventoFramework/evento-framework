package com.evento.application.client;

import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.personaldata.ForgetSubjectRequest;
import com.evento.common.personaldata.ForgetSubjectResponse;
import com.evento.common.personaldata.SubjectKeyProvider;
import com.evento.common.personaldata.SubjectKeyRequest;
import com.evento.common.personaldata.SubjectKeyResponse;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The keys of data subjects as the Evento servers keep them (3.0, crypto-shredding): asked over the
 * bus, kept here for {@code cacheTtl} so that reading a stream of events does not ask once per
 * event. A subject forgotten elsewhere can still be read here until its cached key expires; a
 * subject forgotten through this bundle is dropped from the cache at once. Absent keys are not
 * cached: a key another bundle creates is seen on the next read.
 */
public final class ServerSubjectKeyProvider implements SubjectKeyProvider {

    private static final long TIMEOUT_MS = 10_000;

    private record Cached(byte[] key, long until) {
    }

    private final EventoServer server;
    private final long ttlNanos;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ServerSubjectKeyProvider(EventoServer server, Duration cacheTtl) {
        this.server = server;
        this.ttlNanos = cacheTtl == null || cacheTtl.isNegative() ? 0 : cacheTtl.toNanos();
    }

    @Override
    public Optional<byte[]> key(String subject, boolean create) {
        long now = System.nanoTime();
        var cached = cache.get(subject);
        if (cached != null && now - cached.until() < 0) return Optional.of(cached.key());
        try {
            SubjectKeyResponse response = server.<SubjectKeyResponse>request(new SubjectKeyRequest(subject, create),
                    TIMEOUT_MS, TimeUnit.MILLISECONDS).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            byte[] key = response == null ? null : response.getKey();
            if (key == null) {
                cache.remove(subject);
                return Optional.empty();
            }
            if (ttlNanos > 0) cache.put(subject, new Cached(key, now + ttlNanos));
            return Optional.of(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while asking the key of a data subject", e);
        } catch (Exception e) {
            throw new IllegalStateException("cannot get the key of data subject " + subject + " from the server: " + e, e);
        }
    }

    @Override
    public boolean forget(String subject) {
        cache.remove(subject);
        try {
            ForgetSubjectResponse response = server.<ForgetSubjectResponse>request(new ForgetSubjectRequest(subject),
                    TIMEOUT_MS, TimeUnit.MILLISECONDS).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return response != null && response.isForgotten();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while forgetting a data subject", e);
        } catch (Exception e) {
            throw new IllegalStateException("cannot forget data subject " + subject + ": " + e, e);
        } finally {
            cache.remove(subject);
        }
    }
}
