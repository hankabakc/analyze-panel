package com.hankabakc.analyzepanel.psychtest.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * BourdonScaleDefinition: Bourdon Dikkat Testi (Harf Izgarası Ölçeği).
 * 
 * S-021 Kararı (Kullanıcı onayı, 2026-09-10):
 * Kaynak dokümandaki (BOURDON dikkat testi[1].doc) 30 satırlık harf ızgarası birebir
 * transkribe edilmiş ve harfler küçük harfe dönüştürülerek (OCR artığı 1->l, ı->i, ş->s normalize edilerek)
 * saklanmıştır. Doküman başlığındaki eski teorik sayılar yerine öğrencinin ekranda gördüğü fiili harfler
 * baz alınmaktadır:
 * - Blok 1 (Satır 1-10):  11 a, 10 b, 10 d, 10 g (Toplam 41 hedef harf)
 * - Blok 2 (Satır 11-20): 10 a,  9 b, 10 d,  9 g (Toplam 38 hedef harf)
 * - Blok 3 (Satır 21-30): 10 a, 11 b,  9 d, 10 g (Toplam 40 hedef harf)
 * - Genel Toplam: 31 a, 30 b, 29 d, 29 g (Toplam 119 hedef harf)
 * 
 * Süre Kuralı (APP-01 §2.1):
 * - Standart süre: 180 saniye (3 dakika).
 * - Ağ toleransı: 5 saniye (maksimum 185 saniye).
 */
@Component
public class BourdonScaleDefinition {

    public static final String TEST_CODE = "BOURDON";
    public static final String TEST_TITLE = "Bourdon Dikkat Testi";
    public static final String TEST_DESCRIPTION = "30 satırlık harf ızgarasında a, b, d, g harflerinin 3 dakika içinde işaretlenmesine dayalı dikkat ve odaklanma testi.";

    public static final int DURATION_SECONDS = 180;
    public static final int TIMEOUT_TOLERANCE_SECONDS = 5;
    public static final int TOTAL_ROWS = 30;
    public static final int COLUMNS_PER_ROW = 22;
    public static final int BLOCK_COUNT = 3;
    public static final int ROWS_PER_BLOCK = 10;

    public static final int BLOCK_1_TARGET_COUNT = 41;
    public static final int BLOCK_2_TARGET_COUNT = 38;
    public static final int BLOCK_3_TARGET_COUNT = 40;
    public static final int TOTAL_TARGET_COUNT = 119;

    private static final List<String> GRID_ROWS = List.of(
        "aepznzsuahvklasibiouoe", // Satır  1
        "rvbpmibirbsmntdaufcfka", // Satır  2
        "ckahseyphbpsdgyzdvrifg", // Satır  3
        "ydvcoyerzhezsegmkfzday", // Satır  4
        "fsdyibtdhmlriemtgtbdfu", // Satır  5
        "kcickokostluzugmaflvut", // Satır  6
        "izrfoudvhypnbpmvhnngry", // Satır  7
        "pvklntyorzncphtemzioim", // Satır  8
        "ralygsoivainarchodbfph", // Satır  9
        "kubsyguemkltcgvgripcte", // Satır 10
        "citelrnzfudtmshdkufdsm", // Satır 11
        "sivetcplrgvgctlrmeugye", // Satır 12
        "bokehbukopfidohoraniav", // Satır 13
        "iosgylarmifbzmelhpznzr", // Satır 14
        "oytnakvpykgvnhvmpbnpyh", // Satır 15
        "vduofrhituvluamfacults", // Satır 16
        "okokcickufsbtgtmeinizh", // Satır 17
        "dtbiyasfyndzfkmgeszehz", // Satır 18
        "renocvdyfflrvdzvgdzpbe", // Satır 19
        "pycaascgcahtnmpbribikp", // Satır 20
        "afnpvdmtoymilgdeotocnt", // Satır 21
        "lupznkrhpucboygudvyaol", // Satır 22
        "szoapfftcvkirbpmnerges", // Satır 23
        "bahvihsckzrfdracgynmhy", // Satır 24
        "tdsvcgzyfmptrogeuubbyh", // Satır 25
        "iuanyadumfapyzebkdbolz", // Satır 26
        "elzheadztclpryfmsnvicv", // Satır 27
        "sbivmzgpsmrkbkrehcuvns", // Satır 28
        "flsleiolglkthzoktdearh", // Satır 29
        "fmiucftibsgkmknphvbgui" // Satır 30
    );

    public List<String> getGridRows() {
        return Collections.unmodifiableList(GRID_ROWS);
    }

    public static boolean isTargetChar(char ch) {
        char lower = Character.toLowerCase(ch);
        return lower == 'a' || lower == 'b' || lower == 'd' || lower == 'g';
    }

    public char getCharAt(int row, int col) {
        if (row < 0 || row >= TOTAL_ROWS || col < 0 || col >= COLUMNS_PER_ROW) {
            throw new IllegalArgumentException("Geçersiz hücre koordinatı: row=" + row + ", col=" + col);
        }
        return GRID_ROWS.get(row).charAt(col);
    }

    public boolean isTargetAt(int row, int col) {
        return isTargetChar(getCharAt(row, col));
    }

    public static int getBlockIndex(int row) {
        if (row < 0 || row >= TOTAL_ROWS) {
            throw new IllegalArgumentException("Geçersiz satır no: " + row);
        }
        return row / ROWS_PER_BLOCK; // 0: 0-9, 1: 10-19, 2: 20-29
    }

    public int getTargetCountForBlock(int blockIndex) {
        return switch (blockIndex) {
            case 0 -> BLOCK_1_TARGET_COUNT;
            case 1 -> BLOCK_2_TARGET_COUNT;
            case 2 -> BLOCK_3_TARGET_COUNT;
            default -> throw new IllegalArgumentException("Geçersiz blok indeksi: " + blockIndex);
        };
    }
}
