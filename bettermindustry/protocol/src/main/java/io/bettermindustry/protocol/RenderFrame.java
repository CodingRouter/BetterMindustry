package io.bettermindustry.protocol;

import java.util.List;

public record RenderFrame(
    int protocolVersion,
    long sequence,
    long renderTimeMillis,
    String focusSectorId,
    String controlOwnerId,
    List<SectorFrame> sectors
) {
    public static final int VERSION = 2;

    public RenderFrame {
        sectors = List.copyOf(sectors);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        json.append("\"protocol\":\"bettermindustry.render.v").append(protocolVersion).append("\",");
        json.append("\"protocolVersion\":").append(protocolVersion).append(',');
        json.append("\"sequence\":").append(sequence).append(',');
        json.append("\"renderTimeMillis\":").append(renderTimeMillis).append(',');
        json.append("\"focusSectorId\":\"").append(escape(focusSectorId)).append("\",");
        json.append("\"controlOwnerId\":");
        if (controlOwnerId == null) json.append("null");
        else json.append('"').append(escape(controlOwnerId)).append('"');
        json.append(',');
        json.append("\"authoritative\":false,");
        json.append("\"worldDataAvailable\":")
            .append(sectors.stream().anyMatch(SectorFrame::worldDataAvailable)).append(',');
        json.append("\"sectors\":[");
        for (int i = 0; i < sectors.size(); i++) {
            if (i > 0) json.append(',');
            sectors.get(i).appendJson(json);
        }
        json.append("]}");
        return json.toString();
    }

    public record SectorFrame(
        String id,
        String map,
        int port,
        String state,
        boolean alive,
        boolean portOpen,
        double gameTick,
        double gameTimeSeconds,
        long updateId,
        int wave,
        boolean paused,
        int players,
        int units,
        int enemies,
        int buildings,
        long diagnosticAgeMillis,
        String viewMode,
        boolean worldDataAvailable,
        double worldSnapshotTick,
        long worldSnapshotAgeMillis,
        long worldSnapshotVersion
    ) {
        private void appendJson(StringBuilder json) {
            json.append('{');
            json.append("\"id\":\"").append(escape(id)).append("\",");
            json.append("\"map\":\"").append(escape(map)).append("\",");
            json.append("\"port\":").append(port).append(',');
            json.append("\"state\":\"").append(escape(state)).append("\",");
            json.append("\"alive\":").append(alive).append(',');
            json.append("\"portOpen\":").append(portOpen).append(',');
            json.append("\"gameTick\":").append(gameTick).append(',');
            json.append("\"gameTimeSeconds\":").append(gameTimeSeconds).append(',');
            json.append("\"updateId\":").append(updateId).append(',');
            json.append("\"wave\":").append(wave).append(',');
            json.append("\"paused\":").append(paused).append(',');
            json.append("\"players\":").append(players).append(',');
            json.append("\"units\":").append(units).append(',');
            json.append("\"enemies\":").append(enemies).append(',');
            json.append("\"buildings\":").append(buildings).append(',');
            json.append("\"diagnosticAgeMillis\":").append(diagnosticAgeMillis).append(',');
            json.append("\"viewMode\":\"").append(escape(viewMode)).append("\",");
            json.append("\"worldDataAvailable\":").append(worldDataAvailable).append(',');
            json.append("\"worldSnapshotTick\":").append(worldSnapshotTick).append(',');
            json.append("\"worldSnapshotAgeMillis\":").append(worldSnapshotAgeMillis).append(',');
            json.append("\"worldSnapshotVersion\":").append(worldSnapshotVersion);
            json.append('}');
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }
}
