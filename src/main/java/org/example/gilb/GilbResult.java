package org.example.gilb;

import java.util.List;

public record GilbResult(
        int cl,
        int totalOperators,
        double relative,
        int maxNesting,
        List<ControlItem> controls,
        List<StmtItem> statements,
        List<String> warnings) {

    public record ControlItem(String type, int line, int depth, int weight) {}

    public record StmtItem(int line, String text) {}

    public int maxNestingFromZero() {
        return Math.max(0, maxNesting - 1);
    }

    public String relativeText() {
        return String.format("%.4f", relative);
    }
}