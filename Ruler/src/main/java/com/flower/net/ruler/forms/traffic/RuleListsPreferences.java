package com.flower.net.ruler.forms.traffic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flower.net.conntrack.allowdenylist.AddressFilterList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import static com.flower.net.ruler.forms.traffic.RuleListForm.YAML_MAPPER;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Persists rule list tabs across restarts in a dedicated preferences node:
 * <ul>
 *   <li>{@value #INDEX_KEY} - all lists in tab order: file reference for file-based lists, key reference for untitled ones;</li>
 *   <li>{@value #UNTITLED_KEY_PREFIX}N - rules of an untitled list (one value each, as values are limited to
 *   {@link Preferences#MAX_VALUE_LENGTH} chars). The key sticks to the list for its lifetime, so if the current
 *   version can't be saved, the previously saved one is kept and restored.</li>
 * </ul>
 * File-based lists are re-read from their files on restore. Every save removes all keys not referenced
 * by the new index, so nothing is left dangling.
 */
class RuleListsPreferences {
    final static Logger LOGGER = LoggerFactory.getLogger(RuleListsPreferences.class);

    static final String NODE_PATH = "com/flower/net/ruler/trafficControl/ruleLists";
    static final String INDEX_KEY = "index";
    static final String UNTITLED_KEY_PREFIX = "untitled.";

    enum EntryType { FILE, UNTITLED }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Entry(EntryType type, @Nullable String file, @Nullable String key, @Nullable String name, boolean active) {}

    record Index(List<Entry> lists) {}

    static Preferences node() {
        return Preferences.userRoot().node(NODE_PATH);
    }

    /** Restore lists in saved order. Never fails: unreadable files become placeholders, unreadable untitled lists - empty. */
    static List<RuleListForm> load() {
        return load(node());
    }

    static List<RuleListForm> load(Preferences node) {
        String indexYaml = node.get(INDEX_KEY, null);
        if (indexYaml == null) {
            return List.of();
        }

        Index index;
        try {
            index = YAML_MAPPER.readValue(indexYaml, Index.class);
        } catch (Exception e) {
            LOGGER.error("Can't read rule lists index from preferences", e);
            return List.of();
        }

        List<RuleListForm> ruleLists = new ArrayList<>();
        for (Entry entry : index.lists()) {
            switch (entry.type()) {
                case FILE -> {
                    if (entry.file() == null) {
                        LOGGER.warn("Skipping file-based rule list entry without a file");
                        continue;
                    }
                    File file = new File(entry.file());
                    RuleListForm ruleList;
                    try {
                        ruleList = RuleListForm.fromFile(file);
                        ruleList.setActive(entry.active());
                    } catch (Exception e) {
                        LOGGER.warn("Can't load rule list from {}, restoring a placeholder", file.getPath(), e);
                        ruleList = RuleListForm.fileNotFound(file);
                    }
                    ruleLists.add(ruleList);
                }
                case UNTITLED -> {
                    AddressFilterList rules = null;
                    String rulesYaml = entry.key() == null ? null : node.get(entry.key(), null);
                    if (rulesYaml == null) {
                        LOGGER.warn("Rules for untitled list \"{}\" not found in preferences, restoring it empty", entry.name());
                    } else {
                        try {
                            rules = YAML_MAPPER.readValue(rulesYaml, AddressFilterList.class);
                        } catch (Exception e) {
                            LOGGER.error("Can't read rules for untitled list \"{}\", restoring it empty", entry.name(), e);
                        }
                    }
                    String name = entry.name() == null ? TrafficControlForm.UNTITLED_LIST_NAME.trim() : entry.name();
                    RuleListForm ruleList = RuleListForm.restoreUntitled(name, rules);
                    ruleList.setActive(entry.active());
                    if (rulesYaml != null) {
                        ruleList.preferencesKey = entry.key();
                    }
                    ruleLists.add(ruleList);
                }
            }
        }
        return ruleLists;
    }

    /**
     * Save lists in the given (tab) order. If an untitled list can't be saved (e.g. doesn't fit into a preference
     * value), its previously saved version is kept; if there is none, the list is left out.
     * UI thread only.
     * @return error messages for anything that couldn't be saved; empty on success
     */
    static List<String> save(List<RuleListForm> ruleLists) {
        return save(node(), ruleLists);
    }

    static List<String> save(Preferences node, List<RuleListForm> ruleLists) {
        List<String> errors = new ArrayList<>();
        try {
            Set<String> existingKeys = Set.of(node.keys());
            // Keys owned by open lists, plus keys still in the node, are not handed out to new lists
            Set<String> takenKeys = new HashSet<>(existingKeys);
            for (RuleListForm ruleList : ruleLists) {
                if (ruleList.getFile() == null && ruleList.preferencesKey != null) {
                    takenKeys.add(ruleList.preferencesKey);
                }
            }

            List<Entry> entries = new ArrayList<>();
            Map<String, String> values = new LinkedHashMap<>();
            Set<String> keptKeys = new HashSet<>();
            int nextKeyNumber = 0;

            // Prepare and validate everything before writing, so a failure never leaves the index and lists inconsistent
            for (RuleListForm ruleList : ruleLists) {
                boolean active = ruleList.activeProperty().get();
                File file = ruleList.getFile();
                if (file != null) {
                    ruleList.preferencesKey = null;
                    entries.add(new Entry(EntryType.FILE, file.getPath(), null, null, active));
                    continue;
                }

                String error = null;
                String rulesYaml = null;
                try {
                    rulesYaml = YAML_MAPPER.writeValueAsString(ruleList.getRules());
                    if (rulesYaml.length() > Preferences.MAX_VALUE_LENGTH) {
                        error = "is too large (" + rulesYaml.length() + " of max " + Preferences.MAX_VALUE_LENGTH + " chars)";
                    }
                } catch (Exception e) {
                    LOGGER.error("Can't serialize rule list \"{}\"", ruleList.getListName(), e);
                    error = "can't be serialized: " + e;
                }

                String key = ruleList.preferencesKey;
                if (error == null) {
                    if (key == null) {
                        while (takenKeys.contains(UNTITLED_KEY_PREFIX + nextKeyNumber)) {
                            nextKeyNumber++;
                        }
                        key = UNTITLED_KEY_PREFIX + nextKeyNumber;
                        takenKeys.add(key);
                        ruleList.preferencesKey = key;
                    }
                    values.put(key, checkNotNull(rulesYaml));
                } else if (key != null && existingKeys.contains(key)) {
                    LOGGER.error("Rule list \"{}\" {}; keeping its previously saved version", ruleList.getListName(), error);
                    errors.add("\"" + ruleList.getListName() + "\" " + error + " - its previously saved version will be restored");
                    keptKeys.add(key);
                } else {
                    LOGGER.error("Rule list \"{}\" {}; no previously saved version to keep", ruleList.getListName(), error);
                    errors.add("\"" + ruleList.getListName() + "\" " + error + " - it will be lost");
                    continue;
                }
                entries.add(new Entry(EntryType.UNTITLED, null, key, ruleList.getListName(), active));
            }

            String indexYaml = YAML_MAPPER.writeValueAsString(new Index(entries));
            if (indexYaml.length() > Preferences.MAX_VALUE_LENGTH) {
                LOGGER.error("Rule lists index is too large to be kept in preferences: {} > {} chars",
                        indexYaml.length(), Preferences.MAX_VALUE_LENGTH);
                errors.add("List of open rule lists is too large (" + indexYaml.length()
                        + " of max " + Preferences.MAX_VALUE_LENGTH + " chars) - the previously saved state will be restored");
                return errors;
            }

            // Lists first, index next, cleanup last: an interruption may leave unused keys, but never a missing list
            values.forEach(node::put);
            node.put(INDEX_KEY, indexYaml);
            for (String key : node.keys()) {
                if (!key.equals(INDEX_KEY) && !values.containsKey(key) && !keptKeys.contains(key)) {
                    node.remove(key);
                }
            }
            node.flush();
        } catch (Exception e) {
            LOGGER.error("Can't save rule lists to preferences", e);
            errors.add("Can't save rule lists to preferences: " + e);
        }
        return errors;
    }

    private RuleListsPreferences() {}
}
