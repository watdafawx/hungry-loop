package dev.watdafawx.hungryloop.client;

/** Client-only: which page of the loop's contents tooltip is showing. Bumped by scrolling over it. */
public final class TooltipPager {

    public static final int LINES_PER_PAGE = 14;

    public static int page = 0;

    private TooltipPager() {}

    public static void scroll(int direction) {
        page += direction;
        if (page < 0) page = 0;
    }
}
