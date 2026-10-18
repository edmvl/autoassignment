(function (AJS) {
    'use strict';

    var $ = AJS.$;
    var CONFIG = window.AUTOASSIGN || {};
    var REST = AJS.contextPath() + '/rest/autoassignment/1.0';
    var USER_PICKER = AJS.contextPath() + '/rest/api/2/user/picker';
    var USER_REST = AJS.contextPath() + '/rest/api/2/user';

    var OPERATOR_LABELS = {
        EQUALS: 'равно',
        NOT_EQUALS: 'не равно',
        CONTAINS: 'содержит',
        IS_EMPTY: 'пусто',
        IS_NOT_EMPTY: 'не пусто'
    };

    var state = {
        fields: [],
        editingId: null
    };

    var userCache = {};      // userName -> displayName
    var searchTimer = null;

    function restUrl(path) {
        return REST + path;
    }

    function escapeHtml(text) {
        return String(text == null ? '' : text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function getData(path) {
        return $.ajax({ url: path, dataType: 'json' }).then(function (data) {
            return data;
        });
    }

    function request(options) {
        return $.ajax($.extend({
            contentType: 'application/json',
            dataType: 'json',
            headers: { 'X-Atlassian-Token': 'no-check' }
        }, options));
    }

    function errorText(xhr) {
        try {
            var data = JSON.parse(xhr.responseText);
            if (data && data.message) {
                return data.message;
            }
        } catch (e) {
            // not a JSON error body
        }
        return xhr.responseText || xhr.statusText || xhr.status;
    }

    function showMessage(type, text) {
        $('#message').attr('class', 'aui-message ' + type).html('<p>' + text + '</p>').show();
    }

    function hideMessage() {
        $('#message').hide();
    }

    // --------------------------------------------------- multi-select widget

    function appendTag($ms, value, label, onRemove) {
        var $control = $ms.find('.ms-control');
        var $filter = $control.find('.ms-filter');
        var $tag = $('<span class="ms-tag"></span>').attr('data-value', value);
        $tag.append($('<span class="ms-tag-text"></span>').text(label));
        var $remove = $('<span class="ms-remove" title="Убрать">&times;</span>');
        $remove.on('click', function (e) {
            e.stopPropagation();
            onRemove(value);
        });
        $tag.append($remove);
        $filter.before($tag);
    }

    function clearTags($ms) {
        $ms.find('.ms-control .ms-tag').remove();
    }

    function updateMsTags($ms) {
        clearTags($ms);
        $ms.find('.ms-dropdown input[type=checkbox]:checked').each(function () {
            var value = this.value;
            var label = $(this).closest('.check-item').attr('data-display') || value;
            appendTag($ms, value, label, function (v) {
                $ms.find('.ms-dropdown input[type=checkbox]').each(function () {
                    if (this.value === v) {
                        this.checked = false;
                    }
                });
                updateMsTags($ms);
            });
        });
    }

    function renderAsyncTags($ms) {
        clearTags($ms);
        var map = $ms.data('selectedValues') || {};
        Object.keys(map).forEach(function (value) {
            appendTag($ms, value, map[value], function (v) {
                var m = $ms.data('selectedValues') || {};
                delete m[v];
                $ms.data('selectedValues', m);
                $ms.find('.ms-dropdown input[type=checkbox]').each(function () {
                    if (this.value === v) {
                        this.checked = false;
                    }
                });
                renderAsyncTags($ms);
            });
        });
    }

    // Static values (field options, statuses, versions, ...).
    function renderMs($ms, items, selected) {
        $ms.data('source', 'static');
        $ms.data('selectedValues', null);
        var $dropdown = $ms.find('.ms-dropdown');
        $dropdown.empty();
        selected = selected || [];
        if (!items || !items.length) {
            $dropdown.append('<div class="ms-empty">Нет доступных значений</div>');
            updateMsTags($ms);
            return;
        }
        items.forEach(function (item) {
            var checked = selected.indexOf(String(item.value)) >= 0 ? ' checked' : '';
            var search = String(item.label + ' ' + item.value).toLowerCase();
            $dropdown.append(
                '<label class="check-item" data-search="' + escapeHtml(search)
                + '" data-display="' + escapeHtml(item.label) + '">'
                + '<input type="checkbox" value="' + escapeHtml(item.value) + '"' + checked + '> '
                + escapeHtml(item.label)
                + '</label>'
            );
        });
        updateMsTags($ms);
    }

    // Generic async picker (server-side search).
    function asyncPickerInit($ms, items, multiple, searchFn, allowEmpty) {
        $ms.data('source', 'async');
        $ms.data('multiple', multiple !== false);
        $ms.data('searchFn', searchFn);
        $ms.data('allowEmpty', !!allowEmpty);
        var map = {};
        (items || []).forEach(function (item) {
            map[String(item.value)] = item.label || String(item.value);
        });
        $ms.data('selectedValues', map);
        $ms.find('.ms-dropdown').empty().hide();
        $ms.find('.ms-filter').val('');
        renderAsyncTags($ms);
    }

    function fetchUser(name, cb) {
        $.ajax({ url: USER_REST, data: { username: name }, dataType: 'json' })
            .done(function (user) { cb((user && user.displayName) || name); })
            .fail(function () { cb(name); });
    }

    function resolveUserNames($ms, names) {
        (names || []).forEach(function (name) {
            if (!userCache[name]) {
                fetchUser(name, function (label) {
                    userCache[name] = label;
                    var map = $ms.data('selectedValues') || {};
                    if (map[name]) {
                        map[name] = label;
                        $ms.data('selectedValues', map);
                        renderAsyncTags($ms);
                    }
                });
            }
        });
    }

    function asyncSearch($ms, term) {
        var $dropdown = $ms.find('.ms-dropdown');
        var searchFn = $ms.data('searchFn');
        if (!searchFn) {
            return;
        }
        if (!term && !$ms.data('allowEmpty')) {
            $dropdown.html('<div class="ms-empty">Введите текст для поиска</div>');
            return;
        }
        searchFn(term, function (items) {
            $dropdown.empty();
            if (!items || !items.length) {
                $dropdown.append('<div class="ms-empty">Ничего не найдено</div>');
                return;
            }
            var map = $ms.data('selectedValues') || {};
            items.forEach(function (item) {
                var value = String(item.value);
                var label = item.label || value;
                var checked = map[value] ? ' checked' : '';
                var $item = $('<label class="check-item"></label>')
                    .attr('data-search', (label + ' ' + value).toLowerCase())
                    .attr('data-display', label);
                $item.append('<input type="checkbox" value="' + escapeHtml(value) + '"' + checked + '> ');
                $item.append(document.createTextNode(label));
                $dropdown.append($item);
            });
        });
    }

    function userSearch(term, cb) {
        if (!term) {
            cb([]);
            return;
        }
        $.ajax({
            url: USER_PICKER,
            data: { query: term, maxResults: 20, showAvatar: false },
            dataType: 'json'
        }).done(function (data) {
            var users = (data && data.users) || [];
            cb(users.map(function (user) {
                var name = user.name;
                var label = user.displayName || name;
                userCache[name] = label;
                return { value: name, label: label + ' (' + name + ')' };
            }));
        }).fail(function () {
            cb([]);
        });
    }

    function labelSearch(term, cb) {
        var url = restUrl('/fields/labels/values');
        var params = [];
        if (CONFIG.projectKey) {
            params.push('projectKey=' + encodeURIComponent(CONFIG.projectKey));
        }
        if (term) {
            params.push('query=' + encodeURIComponent(term));
        }
        if (params.length) {
            url += '?' + params.join('&');
        }
        $.ajax({ url: url, dataType: 'json' }).done(function (values) {
            cb((values || []).map(function (v) {
                return { value: v.value, label: v.value };
            }));
        }).fail(function () {
            cb([]);
        });
    }

    function msValues($ms) {
        if ($ms.data('source') === 'async') {
            return Object.keys($ms.data('selectedValues') || {});
        }
        return $ms.find('.ms-dropdown input[type=checkbox]:checked').map(function () {
            return this.value;
        }).get();
    }

    // ---------------------------------------------------------------- fields

    function buildFieldSelect() {
        var html = '<select class="select condition-field">';
        state.fields.forEach(function (field) {
            html += '<option value="' + escapeHtml(field.id) + '">' + escapeHtml(field.name) + '</option>';
        });
        html += '</select>';
        return html;
    }

    function buildOperatorOptions() {
        var html = '';
        Object.keys(OPERATOR_LABELS).forEach(function (key) {
            html += '<option value="' + key + '">' + OPERATOR_LABELS[key] + '</option>';
        });
        return html;
    }

    function isUserField(fieldId) {
        if (fieldId === 'assignee' || fieldId === 'reporter') {
            return true;
        }
        var field = state.fields.filter(function (f) { return f.id === fieldId; })[0];
        return !!(field && field.type && field.type.indexOf('userpicker') >= 0);
    }

    // ------------------------------------------------------------ conditions

    function addConditionRow(condition) {
        condition = condition || {};
        var selectedValues = condition.values || (condition.value ? [condition.value] : []);

        var $row = $(
            '<div class="condition-row">' +
            '  <div class="condition-cell">' + buildFieldSelect() + '</div>' +
            '  <div class="condition-cell"><select class="select condition-operator">' + buildOperatorOptions() + '</select></div>' +
            '  <div class="condition-cell">' +
            '    <input class="text condition-value" type="text" placeholder="Значение">' +
            '    <div class="ms condition-ms" style="display:none">' +
            '      <div class="ms-control"><input type="text" class="ms-filter" placeholder="Поиск..."></div>' +
            '      <div class="ms-dropdown" style="display:none"></div>' +
            '    </div>' +
            '  </div>' +
            '  <button class="aui-button aui-button-light remove-condition" type="button">Удалить</button>' +
            '</div>'
        );

        if (condition.fieldId) {
            $row.find('.condition-field').val(condition.fieldId);
        }
        if (condition.operator) {
            $row.find('.condition-operator').val(condition.operator);
        }
        if (selectedValues.length) {
            $row.find('.condition-value').val(selectedValues.join(', '));
        }
        applyOperatorState($row);

        $('#rule-conditions').append($row);

        var currentFieldId = $row.find('.condition-field').val();
        applyDateInput($row, currentFieldId);
        loadFieldValues($row, currentFieldId, selectedValues);
    }

    function isDateField(fieldId) {
        if (fieldId === 'duedate' || fieldId === 'created' || fieldId === 'updated') {
            return true;
        }
        var field = state.fields.filter(function (f) { return f.id === fieldId; })[0];
        return !!(field && field.type
            && (field.type.indexOf('datepicker') >= 0 || field.type.indexOf('datetime') >= 0));
    }

    function applyDateInput($row, fieldId) {
        var $input = $row.find('.condition-value');
        $input.attr('type', isDateField(fieldId) ? 'date' : 'text');
    }

    function applyOperatorState($row) {
        var op = $row.find('.condition-operator').val();
        var empty = (op === 'IS_EMPTY' || op === 'IS_NOT_EMPTY');
        $row.find('.condition-value').prop('disabled', empty);
        $row.find('.condition-ms input').prop('disabled', empty);
    }

    function loadFieldValues($row, fieldId, selectedValues) {
        var $input = $row.find('.condition-value');
        var $ms = $row.find('.condition-ms');
        $row.data('hasOptions', false);
        $ms.hide();
        $ms.find('.ms-dropdown').empty().hide();
        $input.show();

        if (!fieldId) {
            return;
        }

        if (isUserField(fieldId)) {
            asyncPickerInit($ms,
                (selectedValues || []).map(function (v) {
                    return { value: v, label: userCache[v] || v };
                }),
                true, userSearch, false);
            resolveUserNames($ms, selectedValues || []);
            $row.data('hasOptions', true);
            $input.hide();
            $ms.show();
            return;
        }

        if (fieldId === 'labels') {
            asyncPickerInit($ms,
                (selectedValues || []).map(function (v) {
                    return { value: v, label: v };
                }),
                true, labelSearch, true);
            $row.data('hasOptions', true);
            $input.hide();
            $ms.show();
            return;
        }

        var url = restUrl('/fields/' + encodeURIComponent(fieldId) + '/values');
        if (CONFIG.projectKey) {
            url += '?projectKey=' + encodeURIComponent(CONFIG.projectKey);
        }

        $.ajax({ url: url, dataType: 'json' }).done(function (values) {
            if (!values || values.length === 0) {
                return;
            }
            var items = values.map(function (value) {
                return { value: value.value, label: value.label };
            });
            renderMs($ms, items, selectedValues || []);
            $row.data('hasOptions', true);
            $input.hide();
            $ms.show();
        });
    }

    function collectConditions() {
        var conditions = [];
        $('#rule-conditions .condition-row').each(function () {
            var $row = $(this);
            var fieldId = $row.find('.condition-field').val();
            var operator = $row.find('.condition-operator').val();
            var values;
            if ($row.data('hasOptions')) {
                values = msValues($row.find('.condition-ms'));
            } else {
                var text = $row.find('.condition-value').val();
                values = text ? [text] : [];
            }
            if (!fieldId || !operator) {
                return;
            }
            conditions.push({ fieldId: fieldId, operator: operator, values: values });
        });
        return conditions;
    }

    // ----------------------------------------------------------------- users

    function selectedUserItems(names) {
        return (names || []).map(function (name) {
            return { value: name, label: userCache[name] || name };
        });
    }

    function populateTargets(rule) {
        rule = rule || {};
        asyncPickerInit($('#rule-assignees'), selectedUserItems(rule.assignees), false, userSearch, false);
        asyncPickerInit($('#rule-coassignees'), selectedUserItems(rule.coAssignees), true, userSearch, false);
        asyncPickerInit($('#rule-watchers'), selectedUserItems(rule.watchers), true, userSearch, false);
        resolveUserNames($('#rule-assignees'), rule.assignees || []);
        resolveUserNames($('#rule-coassignees'), rule.coAssignees || []);
        resolveUserNames($('#rule-watchers'), rule.watchers || []);
    }

    function userNames(names) {
        if (!names || !names.length) {
            return '';
        }
        return names.map(function (name) {
            return userCache[name] || name;
        }).join(', ');
    }

    // ----------------------------------------------------------------- rules

    function fieldName(fieldId) {
        var field = state.fields.filter(function (f) { return f.id === fieldId; })[0];
        return field ? field.name : fieldId;
    }

    function displayValue(fieldId, value) {
        if (isUserField(fieldId) && userCache[value]) {
            return userCache[value];
        }
        return value;
    }

    function conditionChip(condition) {
        var op = OPERATOR_LABELS[condition.operator] || condition.operator;
        var text;
        if (condition.operator === 'IS_EMPTY' || condition.operator === 'IS_NOT_EMPTY') {
            text = fieldName(condition.fieldId) + ' ' + op;
        } else {
            var values = condition.values || (condition.value ? [condition.value] : []);
            text = fieldName(condition.fieldId) + ' ' + op + ' '
                + values.map(function (v) { return displayValue(condition.fieldId, v); }).join(' / ');
        }
        return $('<span class="condition-chip">').text(text);
    }

    function addTargetChip($container, label, names) {
        if (!names || !names.length) {
            return;
        }
        var $chip = $('<span class="target-chip">');
        $chip.append($('<span class="target-label">').text(label + ':'));
        $chip.append(document.createTextNode(' ' + userNames(names)));
        $container.append($chip);
    }

    function ruleCard(rule, index) {
        var $card = $('<div class="rule-card" draggable="true">').attr('data-id', rule.id);
        $card.append('<div class="rule-card-handle" title="Перетащите, чтобы изменить приоритет">&#8942;&#8942;</div>');

        var $main = $('<div class="rule-card-main">');

        var $header = $('<div class="rule-card-header">');
        $header.append($('<span class="rule-priority">').text(index + 1));
        $header.append($('<span class="rule-name">').text(rule.name || '(без названия)'));
        $header.append($('<span class="rule-status ' + (rule.enabled ? 'enabled' : 'disabled') + '">')
            .text(rule.enabled ? 'Активно' : 'Выключено'));

        var $actions = $('<div class="rule-card-actions">');
        var $edit = $('<button class="aui-button aui-button-light" type="button">Изменить</button>');
        var $delete = $('<button class="aui-button aui-button-light" type="button">Удалить</button>');
        $edit.on('click', function () { openEditDialog(rule); });
        $delete.on('click', function () { deleteRule(rule); });
        $actions.append($edit).append($delete);
        $header.append($actions);
        $main.append($header);

        var $targets = $('<div class="rule-card-targets">');
        addTargetChip($targets, 'Исполнитель', rule.assignees);
        addTargetChip($targets, 'Соисполнители', rule.coAssignees);
        addTargetChip($targets, 'Наблюдатели', rule.watchers);
        if ($targets.children().length) {
            $main.append($targets);
        }

        var $conditions = $('<div class="rule-card-conditions">');
        if (!rule.conditions || rule.conditions.length === 0) {
            $conditions.append('<span class="condition-chip condition-chip-empty">без условий — применяется всегда</span>');
        } else {
            rule.conditions.forEach(function (condition) {
                $conditions.append(conditionChip(condition));
            });
        }
        $main.append($conditions);

        $card.append($main);
        return $card;
    }

    function renderRules(rules) {
        $('#rules-list').empty();
        $('#no-rules').toggle(!rules || rules.length === 0);
        (rules || []).forEach(function (rule, index) {
            $('#rules-list').append(ruleCard(rule, index));
        });
    }

    // ---------------------------------------------------------------- dialog

    var ruleDialog = null;

    function closeDropdowns() {
        $('.ms-dropdown').hide();
        $('.ms-filter').val('');
        $('.ms-dropdown .check-item').show();
    }

    function openCreateDialog() {
        state.editingId = null;
        $('#rule-dialog-title').text('Добавить правило');
        $('#rule-form')[0].reset();
        $('#rule-enabled').prop('checked', true);
        $('#rule-conditions').empty();
        closeDropdowns();
        ruleDialog.show();
        populateTargets(null);
        addConditionRow();
    }

    function openEditDialog(rule) {
        state.editingId = rule.id;
        $('#rule-dialog-title').text('Изменить правило');
        $('#rule-form')[0].reset();
        $('#rule-name').val(rule.name);
        $('#rule-enabled').prop('checked', rule.enabled);
        $('#rule-conditions').empty();
        closeDropdowns();
        ruleDialog.show();
        populateTargets(rule);

        if (rule.conditions && rule.conditions.length > 0) {
            rule.conditions.forEach(addConditionRow);
        } else {
            addConditionRow();
        }
    }

    function collectRule() {
        return {
            id: state.editingId,
            projectKey: CONFIG.projectKey,
            name: $('#rule-name').val(),
            assignees: msValues($('#rule-assignees')),
            coAssignees: msValues($('#rule-coassignees')),
            watchers: msValues($('#rule-watchers')),
            conditions: collectConditions(),
            enabled: $('#rule-enabled').prop('checked')
        };
    }

    function saveRule() {
        hideMessage();
        var rule = collectRule();
        if (!rule.name || rule.name.trim() === '') {
            showMessage('error', 'Укажите название правила.');
            return;
        }
        if (rule.assignees.length === 0 && rule.coAssignees.length === 0 && rule.watchers.length === 0) {
            showMessage('error', 'Выберите хотя бы одну цель: исполнителя, соисполнителей или наблюдателей.');
            return;
        }

        var isUpdate = state.editingId != null;
        var options = isUpdate
            ? { method: 'PUT', url: restUrl('/rules/' + state.editingId), data: JSON.stringify(rule) }
            : { method: 'POST', url: restUrl('/rules'), data: JSON.stringify(rule) };

        request(options)
            .done(function () {
                ruleDialog.hide();
                closeDropdowns();
                showMessage('success', 'Правило сохранено.');
                reloadRules();
            })
            .fail(function (xhr) {
                showMessage('error', 'Не удалось сохранить правило: ' + errorText(xhr));
            });
    }

    function deleteRule(rule) {
        if (!window.confirm('Удалить правило «' + (rule.name || 'без названия') + '»?')) {
            return;
        }
        hideMessage();
        request({
            method: 'DELETE',
            url: restUrl('/rules/' + rule.id + '?projectKey=' + encodeURIComponent(CONFIG.projectKey))
        }).done(function () {
            showMessage('success', 'Правило удалено.');
            reloadRules();
        }).fail(function (xhr) {
            showMessage('error', 'Не удалось удалить правило: ' + errorText(xhr));
        });
    }

    // ---------------------------------------------------------------- loading

    function loadFields() {
        getData(restUrl('/fields')).done(function (fields) {
            state.fields = fields || [];
        }).fail(function () {
            showMessage('error', 'Не удалось загрузить список полей.');
        });
    }

    function reloadRules() {
        if (!CONFIG.projectKey) {
            renderRules([]);
            return;
        }
        getData(restUrl('/rules?projectKey=' + encodeURIComponent(CONFIG.projectKey)))
            .done(renderRules)
            .fail(function (xhr) {
                showMessage('error', 'Не удалось загрузить правила: ' + errorText(xhr));
            });
    }

    function renumberRows() {
        $('#rules-list .rule-card').each(function (index) {
            $(this).find('.rule-priority').text(index + 1);
        });
    }

    function saveOrder() {
        var ids = $('#rules-list .rule-card').map(function () {
            return $(this).data('id');
        }).get();
        if (!ids.length) {
            return;
        }
        request({
            method: 'POST',
            url: restUrl('/reorder'),
            data: JSON.stringify({ projectKey: CONFIG.projectKey, order: ids })
        }).done(function () {
            showMessage('success', 'Приоритет сохранён.');
        }).fail(function (xhr) {
            showMessage('error', 'Не удалось сохранить приоритет: ' + errorText(xhr));
            reloadRules();
        });
    }

    function bindEvents() {
        ruleDialog = {
            show: function () { $('#rule-dialog').css('display', 'flex'); },
            hide: function () { $('#rule-dialog').css('display', 'none'); }
        };

        $('#add-rule-btn').on('click', openCreateDialog);
        $('#rule-save-btn').on('click', saveRule);
        $('#rule-cancel-btn').on('click', function () { ruleDialog.hide(); closeDropdowns(); });
        $('#rule-dialog-close').on('click', function () { ruleDialog.hide(); closeDropdowns(); });
        $('#rule-dialog').on('click', function (e) {
            if (e.target === this) {
                ruleDialog.hide();
                closeDropdowns();
            }
        });
        $(document).on('keydown', function (e) {
            if (e.key === 'Escape' && $('#rule-dialog').is(':visible')) {
                ruleDialog.hide();
                closeDropdowns();
            }
        });

        // Multi-select dropdown behaviour.
        $(document).on('click', '.ms-control', function (e) {
            if ($(e.target).hasClass('ms-remove')) {
                return;
            }
            var $ms = $(this).closest('.ms');
            var $dropdown = $ms.find('.ms-dropdown');
            if ($(e.target).hasClass('ms-filter')) {
                if (!$dropdown.is(':visible')) {
                    $('.ms-dropdown').hide();
                    $dropdown.show();
                }
                return;
            }
            var willOpen = !$dropdown.is(':visible');
            $('.ms-dropdown').hide();
            if (willOpen) {
                $dropdown.show();
                var $filter = $ms.find('.ms-filter');
                $filter.trigger('focus');
                if ($ms.data('source') === 'async') {
                    asyncSearch($ms, $filter.val());
                }
            }
        });

        $(document).on('input', '.ms-filter', function () {
            var $ms = $(this).closest('.ms');
            var term = String($(this).val() || '');
            if ($ms.data('source') === 'async') {
                clearTimeout(searchTimer);
                searchTimer = setTimeout(function () {
                    asyncSearch($ms, term.trim());
                }, 250);
                return;
            }
            var lower = term.toLowerCase();
            $ms.find('.ms-dropdown .check-item').each(function () {
                var $item = $(this);
                var text = $item.data('search') || '';
                $item.toggle(text.indexOf(lower) >= 0);
            });
        });

        $(document).on('change', '.ms-dropdown input[type=checkbox]', function () {
            var $ms = $(this).closest('.ms');
            if ($ms.data('source') === 'async') {
                var name = this.value;
                var label = $(this).closest('.check-item').attr('data-display') || name;
                var map = $ms.data('selectedValues') || {};
                if ($ms.data('multiple') === false) {
                    map = {};
                    $ms.find('.ms-dropdown input[type=checkbox]').not(this).prop('checked', false);
                    if (this.checked) {
                        map[name] = label;
                    }
                    $ms.data('selectedValues', map);
                    renderAsyncTags($ms);
                    if (this.checked) {
                        $ms.find('.ms-dropdown').hide();
                    }
                } else {
                    if (this.checked) {
                        map[name] = label;
                    } else {
                        delete map[name];
                    }
                    $ms.data('selectedValues', map);
                    renderAsyncTags($ms);
                }
                return;
            }
            updateMsTags($ms);
        });

        $(document).on('click', function (e) {
            if (!$(e.target).closest('.ms').length) {
                $('.ms-dropdown').hide();
            }
        });

        $('#add-condition-btn').on('click', function () { addConditionRow(); });

        $('#rule-conditions').on('change', '.condition-field', function () {
            var $row = $(this).closest('.condition-row');
            $row.find('.condition-value').val('');
            applyDateInput($row, $(this).val());
            loadFieldValues($row, $(this).val(), null);
        });

        $('#rule-conditions').on('change', '.condition-operator', function () {
            applyOperatorState($(this).closest('.condition-row'));
        });

        $('#rule-conditions').on('click', '.remove-condition', function () {
            $(this).closest('.condition-row').remove();
        });

        // Drag-and-drop rule ordering.
        var dragRow = null;

        $('#rules-list')
            .on('dragstart', '.rule-card', function (e) {
                dragRow = this;
                $(this).addClass('dragging');
                if (e.originalEvent && e.originalEvent.dataTransfer) {
                    e.originalEvent.dataTransfer.effectAllowed = 'move';
                    try {
                        e.originalEvent.dataTransfer.setData('text/plain', 'row');
                    } catch (x) {
                        // ignore
                    }
                }
            })
            .on('dragover', '.rule-card', function (e) {
                if (!dragRow || this === dragRow) {
                    return;
                }
                e.preventDefault();
                if (e.originalEvent && e.originalEvent.dataTransfer) {
                    e.originalEvent.dataTransfer.dropEffect = 'move';
                }
                var rect = this.getBoundingClientRect();
                var after = (e.originalEvent.clientY - rect.top) > rect.height / 2;
                if (after) {
                    $(this).after(dragRow);
                } else {
                    $(this).before(dragRow);
                }
                renumberRows();
            })
            .on('drop', '.rule-card', function (e) {
                e.preventDefault();
            })
            .on('dragend', '.rule-card', function () {
                if (!dragRow) {
                    return;
                }
                $(dragRow).removeClass('dragging');
                dragRow = null;
                saveOrder();
            });
    }

    function init() {
        if (!$('#rule-dialog').length) {
            return;
        }

        bindEvents();

        if (!CONFIG.projectKey) {
            renderRules([]);
            return;
        }

        loadFields();
        reloadRules();
    }

    $(document).ready(init);
})(AJS);
