package infra.k8s.util;

import java.time.Duration;
import java.time.Instant;

public class TimeUtils {

    public static String calculateHumanAge(String timestampStr) {

        if (timestampStr == null || timestampStr.isBlank()) {
            return "N/A";
        }

        try {

            Instant created = Instant.parse(timestampStr);
            Duration duration = Duration.between(created, Instant.now());

            if (duration.isNegative()) {
                return "0s";
            }

            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;

            StringBuilder sb = new StringBuilder();

            if (days > 0) sb.append(days).append("d");
            if (hours > 0) sb.append(hours).append("h");
            if (minutes > 0) sb.append(minutes).append("m");

            if (sb.length() == 0 && seconds > 0)
                sb.append(seconds).append("s");

            return sb.length() > 0 ? sb.toString() : "<1m";

        } catch (Exception e) {
            return "?";
        }
    }
}