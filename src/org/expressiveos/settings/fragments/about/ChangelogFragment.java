/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.expressiveos.settings.fragments.about;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.search.SearchIndexableRaw;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.expressiveos.settings.utils.NetworkUtils;

@SearchIndexable
public class ChangelogFragment extends SettingsPreferenceFragment {

    private TextView textView;
    private volatile boolean mViewDestroyed;

    private static final String CHANGELOG_URL_TEMPLATE =
            "https://raw.githubusercontent.com/denson9874/expressiveos_ota/main/changelogs/v2/%s.txt";
    private static final String OTA_REPO_URL = "https://github.com/denson9874/expressiveos_ota";

    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)\\s*#*$");
    private static final Pattern MD_BULLET = Pattern.compile("^(\\s*)[-*\\u2022]\\s+(.*)$");
    private static final Pattern MD_INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`");
    private static final Pattern MD_DETECT = Pattern.compile("(?m)^(#{1,3}\\s+|[-*]\\s+)");

    private int getThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, typedValue, true)) {
            return 0;
        }
        return typedValue.resourceId != 0
                ? context.getColor(typedValue.resourceId)
                : typedValue.data;
    }

    private void setSpan(SpannableStringBuilder sb, Object span, int start, int end) {
        if (start < end) {
            sb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.changelog, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = view.getContext();
        final String device = Build.DEVICE;

        textView = view.findViewById(R.id.changelog_text);
        mViewDestroyed = false;
        final TextView targetView = textView;

        new Thread(() -> {
            String data = fetchChangelog(String.format(CHANGELOG_URL_TEMPLATE, device));

            if (data == null) {
                final String fallbackText = context.getString(
                        R.string.changelog_error_no_official, OTA_REPO_URL);
                targetView.post(() -> {
                    if (!mViewDestroyed && textView == targetView) {
                        targetView.setText(fallbackText);
                    }
                });
                return;
            }

            final int accentColor = getThemeColor(context, android.R.attr.colorAccent);
            final int textColor = getThemeColor(context, android.R.attr.textColorPrimary);
            final int secondaryColor = getThemeColor(context, android.R.attr.textColorSecondary);
            final float density = context.getResources().getDisplayMetrics().density;

            final SpannableStringBuilder result = MD_DETECT.matcher(data).find()
                    ? formatMarkdownChangelog(data, accentColor, textColor, secondaryColor, density)
                    : formatLegacyChangelog(data, accentColor, textColor);

            targetView.post(() -> {
                if (!mViewDestroyed && textView == targetView) {
                    targetView.setText(result);
                }
            });
        }).start();
    }

    private SpannableStringBuilder formatMarkdownChangelog(
            String data, int accentColor, int textColor, int secondaryColor, float density) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = data.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        int bulletGapPx = Math.round(14f * density);
        int nestedIndentPx = Math.round(16f * density);
        boolean lastWasBlank = true;

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                if (!lastWasBlank && sb.length() > 0) {
                    sb.append("\n");
                    lastWasBlank = true;
                }
                continue;
            }
            if (trimmed.matches("^[-=_]{3,}$")) {
                continue;
            }

            Matcher headingMatcher = MD_HEADING.matcher(trimmed);
            if (headingMatcher.matches()) {
                if (sb.length() > 0 && !lastWasBlank) {
                    sb.append("\n");
                }
                int level = headingMatcher.group(1).length();
                String headingText = headingMatcher.group(2).trim();
                int start = sb.length();
                appendInlineMarkdown(sb, headingText, accentColor, textColor);
                int end = sb.length();
                float scale = level == 1 ? 1.28f : (level == 2 ? 1.12f : 1.04f);
                setSpan(sb, new RelativeSizeSpan(scale), start, end);
                setSpan(sb, new StyleSpan(Typeface.BOLD), start, end);
                setSpan(sb, new ForegroundColorSpan(level <= 2 ? accentColor : textColor), start, end);
                sb.append("\n");
                lastWasBlank = false;
                continue;
            }

            Matcher bulletMatcher = MD_BULLET.matcher(rawLine);
            if (bulletMatcher.matches()) {
                int indentSpaces = bulletMatcher.group(1).length();
                String itemText = bulletMatcher.group(2).trim();
                int start = sb.length();
                sb.append("\u2022  ");
                setSpan(sb, new ForegroundColorSpan(accentColor), start, start + 1);
                appendInlineMarkdown(sb, itemText, accentColor, textColor);
                int end = sb.length();
                int firstMargin = indentSpaces >= 2 ? nestedIndentPx : 0;
                setSpan(sb, new LeadingMarginSpan.Standard(firstMargin, firstMargin + bulletGapPx), start, end);
                sb.append("\n");
                lastWasBlank = false;
                continue;
            }

            int start = sb.length();
            appendInlineMarkdown(sb, trimmed, accentColor, textColor);
            int end = sb.length();
            if (trimmed.matches("^\\d{4}-\\d{2}-\\d{2}.*$")) {
                setSpan(sb, new ForegroundColorSpan(secondaryColor), start, end);
                setSpan(sb, new StyleSpan(Typeface.BOLD), start, end);
            }
            sb.append("\n");
            lastWasBlank = false;
        }

        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb;
    }

    private void appendInlineMarkdown(
            SpannableStringBuilder sb, String text, int accentColor, int textColor) {
        Matcher m = MD_INLINE.matcher(text);
        int cursor = 0;
        while (m.find()) {
            if (m.start() > cursor) {
                int s = sb.length();
                sb.append(text, cursor, m.start());
                setSpan(sb, new ForegroundColorSpan(textColor), s, sb.length());
            }
            if (m.group(1) != null) {
                int s = sb.length();
                sb.append(m.group(1));
                setSpan(sb, new StyleSpan(Typeface.BOLD), s, sb.length());
                setSpan(sb, new ForegroundColorSpan(textColor), s, sb.length());
            } else if (m.group(2) != null) {
                int s = sb.length();
                sb.append(m.group(2));
                setSpan(sb, new TypefaceSpan("monospace"), s, sb.length());
                setSpan(sb, new ForegroundColorSpan(accentColor), s, sb.length());
            }
            cursor = m.end();
        }
        if (cursor < text.length()) {
            int s = sb.length();
            sb.append(text, cursor, text.length());
            setSpan(sb, new ForegroundColorSpan(textColor), s, sb.length());
        }
    }

    private SpannableStringBuilder formatLegacyChangelog(
            String data, int color, int textColor) {
        Pattern date = Pattern.compile("(={20}|\\d{4}-\\d{2}-\\d{2})");
        Pattern commit = Pattern.compile("([a-f0-9]{7})");
        Pattern committer = Pattern.compile("\\[(\\D.*?)]");
        Pattern title = Pattern.compile("(\\R\\s+[\\*]\\s.*)");
        Pattern notableRomChanges = Pattern.compile("(?m)^Notable ROM changes.*$");

        SpannableStringBuilder sb = new SpannableStringBuilder(data);
        sb.setSpan(new ForegroundColorSpan(textColor), 0, sb.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        Matcher m = date.matcher(data);
        while (m.find()) {
            setSpan(sb, new ForegroundColorSpan(color), m.start(1), m.end(1));
            setSpan(sb, new StyleSpan(Typeface.BOLD), m.start(1), m.end(1));
        }

        m = commit.matcher(data);
        while (m.find()) {
            setSpan(sb, new StyleSpan(Typeface.NORMAL), m.start(1), m.end(1));
        }

        m = committer.matcher(data);
        while (m.find()) {
            setSpan(sb, new ForegroundColorSpan(color), m.start(1), m.end(1));
            setSpan(sb, new StyleSpan(Typeface.NORMAL), m.start(1), m.end(1));
        }

        m = title.matcher(data);
        while (m.find()) {
            setSpan(sb, new ForegroundColorSpan(color), m.start(1), m.end(1));
            setSpan(sb, new StyleSpan(Typeface.BOLD), m.start(1), m.end(1));
        }

        m = notableRomChanges.matcher(data);
        while (m.find()) {
            setSpan(sb, new StyleSpan(Typeface.BOLD), m.start(), m.end());
        }
        return sb;
    }

    @Nullable
    private String fetchChangelog(String urlString) {
        try {
            String data = NetworkUtils.fetchString(urlString, null);
            if (data != null && !data.isEmpty()) {
                return data;
            }
        } catch (IOException ignored) {
        }
        return readLocalChangelog();
    }

    @Nullable
    private String readLocalChangelog() {
        String[] paths = {
            "/system/etc/Changelog.txt",
            "/product/etc/Changelog.txt",
            "/system_ext/etc/Changelog.txt"
        };
        for (String path : paths) {
            java.io.File file = new java.io.File(path);
            if (file.exists() && file.isFile() && file.length() > 0) {
                try {
                    return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        mViewDestroyed = true;
        textView = null;
        super.onDestroyView();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.EXPRESSIVE_STUDIO;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
                    SearchIndexableRaw raw = new SearchIndexableRaw(context);
                    raw.key = "studio_changelog";
                    raw.title = context.getString(R.string.changelog_title);
                    raw.summaryOn = context.getString(R.string.changelog_summary);
                    raw.screenTitle = context.getString(R.string.changelog_title);
                    return Collections.singletonList(raw);
                }
            };
}
