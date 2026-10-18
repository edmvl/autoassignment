(function (AJS) {
    'use strict';

    var $ = AJS.$;
    var CONFIG = window.AUTOASSIGN || {};
    var REST = AJS.contextPath() + '/rest/autoassignment/1.0';

    var OPERATOR_LABELS = {
        EQUALS: 'равно',
        NOT_EQUALS: 'не равно',
        CONTAINS: 'содержит',
        IS_EMPTY: 'пусто',
        IS_NOT_EMPTY: 'не пусто'
    };

    var state = {
        fields: [],
        users: [],
        editingId: null
    };

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
    // Compact searchable multi-select: tags in the control + dropdown with
    // a search field and checkboxes.

    function updateMsTags($ms) {
        var $control = $ms.find('.ms-control');
        var $filter = $control.find('.ms-filter');
        $control.find('.ms-tag').remove();

        $ms.find('.ms-dropdown input[type=checkbox]:checked').each(function () {
            var value = this.value;
            var label = $(this).closest('.check-item').attr('data-label') || value;
            var $tag = $('<span class="ms-tag"></span>').attr('data-value', value);
            $tag.append($('<span class="ms-tag-text"></span>').text(label));
            var $remove = $('<span class="ms-remove" title="Убрать">&times;</span>');
            $remove.on('click', function (e) {
                e.stopPropagation();
                $ms.find('.ms-dropdown input[type=checkbox]').each(function () {
                    if (this.value === value) {
                        this.checked = false;
                    }
                });
                updateMsTags($ms);
            });
            $tag.append($remove);
            $filter.before($tag);
        });
    }

    function renderMs($ms, items, selected) {
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
                + '" data-label="' + escapeHtml(item.label) + '">'
                + '<input type="checkbox" value="' + escapeHtml(item.value) + '"' + checked + '> '
                + escapeHtml(item.label)
                + '</label>'
            );
        });
        updateMsTags($ms);
    }

    function msValues($ms) {
        return $ms.find('.ms-dropdown input[type=checkbox]:checked').map(function () {
            return this.value;
        }).get();
    }

    function userItems() {
        return state.users.map(function (user) {
            return { value: user.name, label: user.displayName + ' (' + user.name + ')' };
        });
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

        // Always load values for the currently selected field (including the default).
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

    function populateSingleSelect(selector, selected) {
        var $select = $(selector);
        $select.empty();
        $select.append('<option value="">— не выбрано —</option>');
        state.users.forEach(function (user) {
            $select.append('<option value="' + escapeHtml(user.name) + '">'
                + escapeHtml(user.displayName) + ' (' + escapeHtml(user.name) + ')</option>');
        });
        if (selected && selected.length) {
            $select.val(selected[0]);
        }
    }

    function populateTargets(rule) {
        rule = rule || {};
        populateSingleSelect('#rule-assignees', rule.assignees || []);
        renderMs($('#rule-coassignees'), userItems(), rule.coAssignees || []);
        renderMs($('#rule-watchers'), userItems(), rule.watchers || []);
    }

    function userNames(names) {
        if (!names || !names.length) {
            return '';
        }
        return names.map(function (name) {
            var user = state.users.filter(function (u) { return u.name === name; })[0];
            return user ? user.displayName : name;
        }).join(', ');
    }

    // ----------------------------------------------------------------- rules

    function conditionsSummary(rule) {
        if (!rule.conditions || rule.conditions.length === 0) {
            return 'всегда';
        }
        return rule.conditions.map(function (c) {
            var fieldName = c.fieldId;
            var field = state.fields.filter(function (f) { return f.id === c.fieldId; })[0];
            if (field) {
                fieldName = field.name;
            }
            var op = OPERATOR_LABELS[c.operator] || c.operator;
            if (c.operator === 'IS_EMPTY' || c.operator === 'IS_NOT_EMPTY') {
                return fieldName + ' ' + op;
            }
            var values = c.values || (c.value ? [c.value] : []);
            return fieldName + ' ' + op + ' «' + values.join(', ') + '»';
        }).join(' И ');
    }

    function renderRules(rules) {
        var $body = $('#rules-table-body');
        $body.empty();

        $('#no-rules').toggle(!rules || rules.length === 0);
        $('#rules-table').toggle(rules && rules.length > 0);

        (rules || []).forEach(function (rule, index) {
            var $row = $('<tr>').attr('data-id', rule.id).attr('draggable', 'true');

            var $priority = $('<td class="priority-col">');
            $priority.append('<span class="drag-handle" title="Перетащите, чтобы изменить приоритет">&#8942;&#8942;</span> ');
            $priority.append($('<span class="priority-num">').text(index + 1));
            $row.append($priority);

            $row.append($('<td>').text(rule.name || '(без названия)'));
            $row.append($('<td>').text(userNames(rule.assignees)));
            $row.append($('<td>').text(userNames(rule.coAssignees)));
            $row.append($('<td>').text(userNames(rule.watchers)));
            $row.append($('<td>').text(conditionsSummary(rule)));
            $row.append($('<td>').text(rule.enabled ? 'Да' : 'Нет'));

            var $actions = $('<td class="rule-actions">');
            var $edit = $('<button class="aui-button aui-button-light edit-rule" type="button">Изменить</button>');
            var $delete = $('<button class="aui-button aui-button-light delete-rule" type="button">Удалить</button>');
            $edit.on('click', function () { openEditDialog(rule); });
            $delete.on('click', function () { deleteRule(rule); });
            $actions.append($edit).append(' ').append($delete);
            $row.append($actions);

            $body.append($row);
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
        var assignee = $('#rule-assignees').val();
        return {
            id: state.editingId,
            projectKey: CONFIG.projectKey,
            name: $('#rule-name').val(),
            assignees: assignee ? [assignee] : [],
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

    function loadUsers() {
        getData(restUrl('/users')).done(function (users) {
            state.users = users || [];
            if ($('#rule-dialog').is(':visible')) {
                populateTargets({
                    assignees: [$('#rule-assignees').val()].filter(Boolean),
                    coAssignees: msValues($('#rule-coassignees')),
                    watchers: msValues($('#rule-watchers'))
                });
            }
        }).fail(function (xhr) {
            showMessage('error', 'Не удалось загрузить пользователей: ' + errorText(xhr));
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
        $('#rules-table-body tr').each(function (index) {
            $(this).find('.priority-num').text(index + 1);
        });
    }

    function saveOrder() {
        var ids = $('#rules-table-body tr').map(function () {
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
                // Clicking the search field must not close an open dropdown.
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
                $ms.find('.ms-filter').trigger('focus');
            }
        });

        $(document).on('input', '.ms-filter', function () {
            var $ms = $(this).closest('.ms');
            var term = String($(this).val() || '').toLowerCase();
            $ms.find('.ms-dropdown .check-item').each(function () {
                var $item = $(this);
                var text = $item.data('search') || '';
                $item.toggle(text.indexOf(term) >= 0);
            });
        });

        $(document).on('change', '.ms-dropdown input[type=checkbox]', function () {
            updateMsTags($(this).closest('.ms'));
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

        $('#rules-table-body')
            .on('dragstart', 'tr', function (e) {
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
            .on('dragover', 'tr', function (e) {
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
            .on('drop', 'tr', function (e) {
                e.preventDefault();
            })
            .on('dragend', 'tr', function () {
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
        loadUsers();
        reloadRules();
    }

    $(document).ready(init);
})(AJS);
