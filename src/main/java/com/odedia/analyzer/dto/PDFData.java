package com.odedia.analyzer.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds extracted PDF data including page content with actual PDF page numbers.
 * The page numbers correspond to the actual PDF pages (1-indexed), even when
 * some pages are empty and filtered out during extraction.
 */
public class PDFData {
    private final List<PageData> pages;
    private final String language;

    /**
     * Legacy constructor for backward compatibility.
     * Assumes sequential page numbering (1, 2, 3, ...).
     */
    public PDFData(List<String> stringPages, String language) {
        this.pages = new ArrayList<>();
        for (int i = 0; i < stringPages.size(); i++) {
            this.pages.add(new PageData(i + 1, stringPages.get(i)));
        }
        this.language = language;
    }

    /**
     * New constructor that accepts pages with their actual PDF page numbers.
     */
    public PDFData(List<PageData> pages, String language, boolean usePageData) {
        this.pages = pages;
        this.language = language;
    }

    /**
     * Returns just the page content strings (for backward compatibility).
     */
    public List<String> getStringPages() {
        List<String> result = new ArrayList<>();
        for (PageData page : pages) {
            result.add(page.getContent());
        }
        return result;
    }

    /**
     * Returns pages with their actual PDF page numbers.
     */
    public List<PageData> getPages() {
        return pages;
    }

    public String getLanguage() {
        return language;
    }

    /**
     * Represents a single page with its actual PDF page number and content.
     */
    public static class PageData {
        private final int actualPageNumber;
        private final String content;

        public PageData(int actualPageNumber, String content) {
            this.actualPageNumber = actualPageNumber;
            this.content = content;
        }

        /**
         * The actual 1-indexed page number from the PDF.
         */
        public int getActualPageNumber() {
            return actualPageNumber;
        }

        /**
         * The extracted text content of this page.
         */
        public String getContent() {
            return content;
        }
    }
}
