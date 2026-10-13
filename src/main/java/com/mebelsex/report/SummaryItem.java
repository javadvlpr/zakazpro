package com.mebelsex.report;

/** A single metric shown above a report table. */
public record SummaryItem(String label, Cell value, Tone tone) {

    public static SummaryItem of(String label, Cell value, Tone tone) {
        return new SummaryItem(label, value, tone);
    }
}
