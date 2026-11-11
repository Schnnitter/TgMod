/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;

import com.exteragram.messenger.ExteraConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuickReplyUtils {

    private static final String PREFERENCES_KEY_PREFIX = "quickReplyTemplates_";
    private static final int MAX_TEMPLATE_LENGTH = 500;
    private static final int MAX_TEMPLATES = 50;

    private QuickReplyUtils() {
    }

    public static void showPicker(Context context, EditText editText, int account, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        ArrayList<String> templates = loadTemplates(context, account, dialogId, resourcesProvider);
        if (templates == null) {
            return;
        }
        CharSequence[] items = new CharSequence[templates.size() + 2];
        for (int i = 0; i < templates.size(); i++) {
            items[i] = expandTemplate(templates.get(i), account, dialogId);
        }
        items[templates.size()] = LocaleController.getString("QuickReplyAdd", R.string.QuickReplyAdd);
        items[templates.size() + 1] = LocaleController.getString("QuickReplyManage", R.string.QuickReplyManage);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("QuickReplies", R.string.QuickReplies));
        builder.setItems(items, (dialog, which) -> {
            if (which < templates.size()) {
                rememberTemplate(context, account, dialogId, templates.get(which), resourcesProvider);
                insertTemplate(editText, expandTemplate(templates.get(which), account, dialogId));
            } else if (which == templates.size()) {
                showAddDialog(context, editText, account, dialogId, resourcesProvider);
            } else {
                showManageDialog(context, account, dialogId, resourcesProvider);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static void showTemplateDuplicateDialog(Context context, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setMessage(LocaleController.getString("QuickReplyDuplicate", R.string.QuickReplyDuplicate));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.show();
    }

    private static void showAddDialog(Context context, EditText editText, int account, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        EditText input = new EditText(context);
        input.setHint(LocaleController.getString("QuickReplyHint", R.string.QuickReplyHint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_TEMPLATE_LENGTH)});

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("QuickReplyAdd", R.string.QuickReplyAdd));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString("Add", R.string.Add), (dialog, which) -> {
            String template = input.getText().toString().trim();
            if (TextUtils.isEmpty(template)) {
                return;
            }
            ArrayList<String> templates = loadTemplates(context, account, dialogId, resourcesProvider);
            if (templates == null) {
                return;
            }
            if (!templates.contains(template)) {
                if (templates.size() >= MAX_TEMPLATES) {
                    showTemplateLimitDialog(context, resourcesProvider);
                    return;
                }
                templates.add(template);
                saveTemplates(account, dialogId, templates);
            }
            insertTemplate(editText, expandTemplate(template, account, dialogId));
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static void showManageDialog(Context context, int account, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        ArrayList<String> templates = loadTemplates(context, account, dialogId, resourcesProvider);
        if (templates == null) {
            return;
        }
        if (templates.isEmpty()) {
            AlertDialog.Builder emptyBuilder = new AlertDialog.Builder(context, resourcesProvider);
            emptyBuilder.setTitle(LocaleController.getString("QuickReplyManage", R.string.QuickReplyManage));
            emptyBuilder.setMessage(LocaleController.getString("QuickReplyEmpty", R.string.QuickReplyEmpty));
            emptyBuilder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            emptyBuilder.show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("QuickReplyManage", R.string.QuickReplyManage));
        builder.setItems(templates.toArray(new CharSequence[0]), (dialog, which) -> {
            String template = templates.get(which);
            showTemplateActionsDialog(context, account, dialogId, template, resourcesProvider);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static void showTemplateActionsDialog(Context context, int account, long dialogId, String template, Theme.ResourcesProvider resourcesProvider) {
        CharSequence[] actions = {
                LocaleController.getString("Edit", R.string.Edit),
                LocaleController.getString("Delete", R.string.Delete)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("QuickReplyManage", R.string.QuickReplyManage));
        builder.setItems(actions, (dialog, which) -> {
            if (which == 0) {
                showEditDialog(context, account, dialogId, template, resourcesProvider);
            } else {
                showDeleteDialog(context, account, dialogId, template, resourcesProvider);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static void showEditDialog(Context context, int account, long dialogId, String template, Theme.ResourcesProvider resourcesProvider) {
        EditText input = createTemplateInput(context, template);
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("Edit", R.string.Edit));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String updatedTemplate = input.getText().toString().trim();
            if (TextUtils.isEmpty(updatedTemplate)) {
                return;
            }
            ArrayList<String> templates = loadTemplates(context, account, dialogId, resourcesProvider);
            if (templates == null) {
                return;
            }
            int index = templates.indexOf(template);
            if (index >= 0) {
                if (!updatedTemplate.equals(template) && templates.contains(updatedTemplate)) {
                    showTemplateDuplicateDialog(context, resourcesProvider);
                    return;
                }
                templates.remove(index);
                templates.add(Math.min(index, templates.size()), updatedTemplate);
                saveTemplates(account, dialogId, templates);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static void showDeleteDialog(Context context, int account, long dialogId, String template, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setMessage(LocaleController.getString("QuickReplyDeleteConfirm", R.string.QuickReplyDeleteConfirm));
        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
            ArrayList<String> templates = loadTemplates(context, account, dialogId, resourcesProvider);
            if (templates == null) {
                return;
            }
            if (templates.remove(template)) {
                saveTemplates(account, dialogId, templates);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static EditText createTemplateInput(Context context, String template) {
        EditText input = new EditText(context);
        input.setText(template);
        input.setSelection(input.length());
        input.setHint(LocaleController.getString("QuickReplyHint", R.string.QuickReplyHint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_TEMPLATE_LENGTH)});
        return input;
    }

    private static void showTemplateLimitDialog(Context context, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setMessage(LocaleController.getString("QuickReplyLimit", R.string.QuickReplyLimit));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.show();
    }

    private static void insertTemplate(EditText editText, String template) {
        int textLength = editText.length();
        Date now = new Date();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
        String expandedTemplate = template.replace("{date}", date).replace("{time}", time);
        int selectionStart = Math.max(0, editText.getSelectionStart());
        int selectionEnd = Math.max(0, editText.getSelectionEnd());
        selectionStart = Math.min(selectionStart, textLength);
        selectionEnd = Math.min(selectionEnd, textLength);
        if (selectionStart > selectionEnd) {
            int selection = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = selection;
        }
        editText.getText().replace(selectionStart, selectionEnd, expandedTemplate);
        editText.setSelection(selectionStart + expandedTemplate.length());
        editText.requestFocus();
    }

    private static void rememberTemplate(Context context, int account, long dialogId, String template, Theme.ResourcesProvider resourcesProvider) {
        ArrayList<String> templates = loadTemplates(context, account, dialogId, resourcesProvider);
        if (templates == null) {
            return;
        }
        if (templates.remove(template)) {
            templates.add(0, template);
            saveTemplates(account, dialogId, templates);
        }
    }

    private static String expandTemplate(String template, int account, long dialogId) {
        Date now = new Date();
        Locale locale = Locale.getDefault();
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("{date}", new SimpleDateFormat("yyyy-MM-dd", locale).format(now));
        values.put("{time}", new SimpleDateFormat("HH:mm", locale).format(now));
        values.put("{date:short}", new SimpleDateFormat("M/d/yy", locale).format(now));
        values.put("{date:long}", new SimpleDateFormat("MMMM d, yyyy", locale).format(now));
        values.put("{time:12h}", new SimpleDateFormat("h:mm a", locale).format(now));
        values.put("{weekday}", new SimpleDateFormat("EEEE", locale).format(now));
        values.put("{year}", new SimpleDateFormat("yyyy", locale).format(now));
        values.put("{month}", new SimpleDateFormat("MMMM", locale).format(now));
        values.put("{day}", new SimpleDateFormat("d", locale).format(now));
        values.put("{datetime}", new SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(now));

        MessagesController messagesController = MessagesController.getInstance(account);
        TLRPC.User user = dialogId > 0 ? messagesController.getUser(dialogId) : null;
        TLRPC.Chat chat = dialogId < 0 ? messagesController.getChat(-dialogId) : null;
        String firstName = null;
        String lastName = null;
        String name = null;
        String username = null;
        if (user != null && !UserObject.isDeleted(user)) {
            firstName = user.first_name;
            lastName = user.last_name;
            name = ContactsController.formatName(user.first_name, user.last_name);
            username = UserObject.getPublicUsername(user);
        } else if (chat != null) {
            name = chat.title;
            username = ChatObject.getPublicUsername(chat);
        }
        addIfPresent(values, "{first_name}", firstName);
        addIfPresent(values, "{last_name}", lastName);
        addIfPresent(values, "{name}", name);
        addIfPresent(values, "{chat}", name);
        addIfPresent(values, "{username}", username);
        addIfPresent(values, "{profile_link}", TextUtils.isEmpty(username) ? null : "https://t.me/" + username);

        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        if (self != null && !UserObject.isDeleted(self)) {
            addIfPresent(values, "{my_name}", ContactsController.formatName(self.first_name, self.last_name));
            addIfPresent(values, "{my_username}", UserObject.getPublicUsername(self));
        }

        String expandedTemplate = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            expandedTemplate = expandedTemplate.replace(entry.getKey(), entry.getValue());
        }
        return expandedTemplate;
    }

    private static void addIfPresent(Map<String, String> values, String token, String value) {
        if (!TextUtils.isEmpty(value)) {
            values.put(token, value);
        }
    }

    private static ArrayList<String> loadTemplates(Context context, int account, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        ArrayList<String> templates = new ArrayList<>();
        String storedTemplates = ExteraConfig.preferences.getString(PREFERENCES_KEY_PREFIX + account + "_" + dialogId, null);
        if (TextUtils.isEmpty(storedTemplates)) {
            return templates;
        }
        try {
            JSONArray jsonArray = new JSONArray(storedTemplates);
            for (int i = 0; i < jsonArray.length(); i++) {
                String template = jsonArray.optString(i, "").trim();
                if (!TextUtils.isEmpty(template)) {
                    templates.add(template);
                }
            }
        } catch (JSONException e) {
            FileLog.e(e);
            AlertDialog.Builder errorBuilder = new AlertDialog.Builder(context, resourcesProvider);
            errorBuilder.setTitle(LocaleController.getString("QuickReplies", R.string.QuickReplies));
            errorBuilder.setMessage(LocaleController.getString("QuickReplyStorageError", R.string.QuickReplyStorageError));
            errorBuilder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            errorBuilder.show();
            return null;
        }
        return templates;
    }

    private static void saveTemplates(int account, long dialogId, List<String> templates) {
        JSONArray jsonArray = new JSONArray();
        for (String template : templates) {
            jsonArray.put(template);
        }
        ExteraConfig.preferences.edit().putString(PREFERENCES_KEY_PREFIX + account + "_" + dialogId, jsonArray.toString()).apply();
    }
}
