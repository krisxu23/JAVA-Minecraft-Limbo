package ua.nanit.limbo.protocol.registry;

import java.util.Arrays;
import java.util.Comparator;

public enum Version {
    V1_7(4, 73, 74, 47),
    V1_8(5, 75, 107, 48),
    V1_9(6, 108, 151, 49),
    V1_9_1(7, 152, 162, 50),
    V1_9_2(8, 163, 167, 51),
    V1_9_4(9, 168, 170, 52),
    V1_10(10, 210, 214, 53),
    V1_11(11, 316, 317, 54),
    V1_11_1(12, 317, 317, 55),
    V1_12(13, 335, 338, 56),
    V1_12_1(14, 338, 338, 57),
    V1_12_2(15, 340, 340, 58),
    V1_13(16, 401, 404, 59),
    V1_13_1(17, 404, 407, 60),
    V1_13_2(18, 408, 410, 61),
    V1_14(19, 471, 491, 62),
    V1_14_1(20, 491, 498, 63),
    V1_14_2(21, 498, 500, 64),
    V1_14_3(22, 500, 510, 65),
    V1_14_4(23, 510, 510, 66),
    V1_15(24, 573, 575, 67),
    V1_15_1(25, 575, 575, 68),
    V1_15_2(26, 575, 578, 69),
    V1_16(27, 735, 751, 70),
    V1_16_1(28, 751, 751, 71),
    V1_16_2(29, 752, 753, 72),
    V1_16_3(30, 754, 756, 73),
    V1_16_4(31, 756, 756, 74),
    V1_17(32, 757, 760, 75),
    V1_17_1(33, 760, 760, 76),
    V1_18(34, 761, 763, 77),
    V1_18_1(35, 763, 763, 78),
    V1_18_2(36, 764, 764, 79),
    V1_19(37, 765, 766, 80),
    V1_19_1(38, 766, 766, 81),
    V1_19_2(39, 767, 767, 82),
    V1_19_3(40, 768, 769, 83),
    V1_19_4(41, 770, 770, 84),
    V1_20(42, 771, 771, 85),
    V1_20_1(43, 772, 772, 86),
    V1_20_2(44, 773, 775, 87),
    V1_20_3(45, 775, 775, 88),
    V1_20_4(46, 776, 776, 89),
    V1_20_5(47, 777, 777, 90),
    V1_21(48, 778, 778, 91),
    V1_21_1(49, 779, 779, 92),
    V1_21_2(50, 780, 780, 93),
    V1_21_3(51, 781, 781, 94);

    private final int protocol;
    private final int min, max;
    private final int mcVersion;

    Version(int protocol, int min, int max, int mcVersion) {
        this.protocol = protocol;
        this.min = min;
        this.max = max;
        this.mcVersion = mcVersion;
    }

    public int getProtocol() { return protocol; }
    public int getMin() { return min; }
    public int getMax() { return max; }
    public int getMcVersion() { return mcVersion; }

    public boolean isSupported() { return mcVersion >= V1_7.mcVersion && mcVersion <= V1_21_3.mcVersion; }
    public boolean moreOrEqual(Version other) { return mcVersion >= other.mcVersion; }
    public boolean less(Version other) { return mcVersion < other.mcVersion; }
    public boolean equals(Version other) { return mcVersion == other.mcVersion; }

    public boolean fromTo(int from, int to) {
        return mcVersion >= from && mcVersion <= to;
    }

    public boolean fromTo(Version from, Version to) {
        return mcVersion >= from.mcVersion && mcVersion <= to.mcVersion;
    }

    public static Version getByProtocol(int protocol) {
        for (Version v : values()) {
            if (v.protocol == protocol) return v;
        }
        return null;
    }

    public static Version getMax() { return V1_21_3; }

    @Override
    public String toString() {
        return "MC" + mcVersion + "(p" + protocol + ")";
    }
}
