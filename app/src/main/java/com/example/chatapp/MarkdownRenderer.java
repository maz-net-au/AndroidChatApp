package com.example.chatapp;

import android.text.Html;
import android.text.Spanned;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class MarkdownRenderer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

    public static Spanned render(String markdown) {
        if (markdown == null) {
            return Html.fromHtml("", Html.FROM_HTML_MODE_LEGACY);
        }
        Node document = PARSER.parse(markdown);
        String html = HTML_RENDERER.render(document);
        // Strip <p> wrappers that add extra block-level line space
        if (html.startsWith("<p>")) {
            html = html.substring(3);
            if (html.endsWith("</p>")) {
                html = html.substring(0, html.length() - 4);
            }
        }
        // Trim trailing whitespace/newlines from the renderer output
        html = html.trim();
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }
}
