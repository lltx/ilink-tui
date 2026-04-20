package com.ilink.tui.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IlinkTuiAppQrLayoutTest {

    @Test
    void qrPanelHeightMatchesRenderedLineCountPlusBorder() {
        String qrView = "row-1\nrow-2\nrow-3\nrow-4";

        int panelHeight = IlinkTuiApp.qrPanelHeight(qrView);

        assertEquals(6, panelHeight);
    }

    @Test
    void qrPanelHeightExpandsBeyondLegacyFixedHeightWhenQrIsTall() {
        String qrView = """
                01
                02
                03
                04
                05
                06
                07
                08
                09
                10
                11
                12
                13
                14
                15
                16
                17
                18
                19
                20
                """;

        int panelHeight = IlinkTuiApp.qrPanelHeight(qrView);

        assertEquals(22, panelHeight);
    }
}
