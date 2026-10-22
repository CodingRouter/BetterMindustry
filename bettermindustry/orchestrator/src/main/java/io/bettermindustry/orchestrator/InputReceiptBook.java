package io.bettermindustry.orchestrator;

import io.bettermindustry.protocol.InputCommandType;
import io.bettermindustry.protocol.InputEnvelope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

final class InputReceiptBook {
    static final long RETENTION_MILLIS = 300_000L;
    static final int MAX_RECEIPTS = 512;

    private final LinkedHashMap<String, Receipt> receipts = new LinkedHashMap<>();

    Admission find(String clientId, InputEnvelope envelope, long now) {
        prune(now);
        Receipt existing = receipts.get(envelope.commandId());
        if (existing == null) return null;
        if (!existing.clientId().equals(clientId)
            || !existing.sectorId().equals(envelope.sectorId())
            || existing.type() != envelope.type()
            || !existing.message().equals(envelope.message())) {
            throw new ConflictException("commandId conflict");
        }
        return new Admission(existing, true);
    }

    Admission begin(String clientId, InputEnvelope envelope, long now) {
        Admission existing = find(clientId, envelope, now);
        if (existing != null) return existing;
        Receipt receipt = new Receipt(
            envelope.commandId(),
            clientId,
            envelope.sectorId(),
            envelope.type(),
            envelope.message(),
            Status.PENDING,
            "",
            -1,
            null,
            now
        );
        receipts.put(receipt.commandId(), receipt);
        prune(now);
        return new Admission(receipt, false);
    }

    Receipt complete(
        String commandId,
        Status status,
        String detail,
        double acknowledgementTick,
        Boolean acknowledgementPaused,
        long now
    ) {
        if (status == Status.PENDING) {
            throw new IllegalArgumentException("completed input receipt cannot remain pending");
        }
        Receipt receipt = receipts.get(commandId);
        if (receipt == null) return null;

        Receipt updated = new Receipt(
            receipt.commandId(),
            receipt.clientId(),
            receipt.sectorId(),
            receipt.type(),
            receipt.message(),
            status,
            detail == null ? "" : detail,
            acknowledgementTick,
            acknowledgementPaused,
            now
        );
        receipts.put(commandId, updated);
        return updated;
    }

    void load(Properties properties, long now) {
        Set<String> commandIds = new LinkedHashSet<>();
        String prefix = "receipt.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            int fieldStart = key.indexOf('.', prefix.length());
            if (fieldStart > prefix.length()) {
                commandIds.add(key.substring(prefix.length(), fieldStart));
            }
        }

        List<Receipt> loaded = new ArrayList<>();
        for (String commandId : commandIds) {
            Receipt receipt = loadReceipt(properties, commandId, now);
            if (receipt != null) loaded.add(receipt);
        }
        loaded.sort(Comparator.comparingLong(Receipt::updatedAtMillis));

        receipts.clear();
        for (Receipt receipt : loaded) {
            receipts.put(receipt.commandId(), receipt);
        }
        prune(now);
    }

    void write(Properties properties) {
        for (Receipt receipt : receipts.values()) {
            String prefix = "receipt." + receipt.commandId() + ".";
            properties.setProperty(prefix + "clientId", receipt.clientId());
            properties.setProperty(prefix + "sectorId", receipt.sectorId());
            properties.setProperty(prefix + "type", receipt.type().name());
            properties.setProperty(prefix + "message", receipt.message());
            properties.setProperty(prefix + "status", receipt.status().name());
            properties.setProperty(prefix + "detail", receipt.detail());
            properties.setProperty(prefix + "acknowledgementTick", Double.toString(receipt.acknowledgementTick()));
            if (receipt.acknowledgementPaused() != null) {
                properties.setProperty(
                    prefix + "acknowledgementPaused",
                    Boolean.toString(receipt.acknowledgementPaused())
                );
            }
            properties.setProperty(prefix + "updatedAtMillis", Long.toString(receipt.updatedAtMillis()));
        }
    }

    void prune(long now) {
        receipts.entrySet().removeIf(entry -> now - entry.getValue().updatedAtMillis() > RETENTION_MILLIS);
        while (receipts.size() > MAX_RECEIPTS) {
            String oldest = receipts.keySet().iterator().next();
            receipts.remove(oldest);
        }
    }

    private static Receipt loadReceipt(Properties properties, String commandId, long now) {
        if (!commandId.matches("[A-Za-z0-9_-]{1,96}")) return null;
        String prefix = "receipt." + commandId + ".";
        try {
            String clientId = required(properties, prefix + "clientId");
            String sectorId = required(properties, prefix + "sectorId");
            if (!sectorId.matches("[a-z0-9-]{1,64}")) return null;
            InputCommandType type = InputCommandType.parse(required(properties, prefix + "type"));
            Status status = Status.valueOf(required(properties, prefix + "status"));
            String detail = properties.getProperty(prefix + "detail", "");
            double acknowledgementTick = Double.parseDouble(
                properties.getProperty(prefix + "acknowledgementTick", "-1")
            );
            String pausedValue = properties.getProperty(prefix + "acknowledgementPaused");
            Boolean acknowledgementPaused = pausedValue == null ? null : Boolean.parseBoolean(pausedValue);
            long updatedAtMillis = Long.parseLong(required(properties, prefix + "updatedAtMillis"));
            if (status == Status.PENDING) {
                status = Status.FAILED;
                detail = "orchestrator restarted before input completion";
                updatedAtMillis = now;
            }
            return new Receipt(
                commandId,
                clientId,
                sectorId,
                type,
                properties.getProperty(prefix + "message", ""),
                status,
                detail,
                acknowledgementTick,
                acknowledgementPaused,
                updatedAtMillis
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value;
    }

    enum Status {
        PENDING, APPLIED, FAILED
    }

    record Admission(Receipt receipt, boolean duplicate) {
    }

    record Receipt(
        String commandId,
        String clientId,
        String sectorId,
        InputCommandType type,
        String message,
        Status status,
        String detail,
        double acknowledgementTick,
        Boolean acknowledgementPaused,
        long updatedAtMillis
    ) {
    }

    static final class ConflictException extends RuntimeException {
        ConflictException(String message) {
            super(message);
        }
    }
}
