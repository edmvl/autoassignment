(function (AJS) {
    'use strict';

    var $ = AJS.$;
    var CONFIG = window.AUTOASSIGN || {};
    var REST = AJS.contextPath() + '/rest/autoassignment/1.0';

    var TARGET_LABELS = {
        ASSIGNEE: 'Исполнитель',
        CO_ASSIGNEE: 'Соисполнитель',
        WATCHERS: 'Наблюдатели'
    };

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

    function request(options) {
        return $.ajax($.extend({
            contentType: 'application/json',
            dataType: 'json'
        }, options));
    }

    // Normalizes a jQuery ajax promise so it resolves with just the response data.
    function getData(path) {
        return $.ajax({ url: path, dataType: 'json' }).then(function (data) {
            return data;
        });
    }

    function showMessage(type, text) {
        var $message = $('#message');
        $message.attr('class', 'aui-message ' + type).html('<p>' + text + '</p>').show();
    }

    function hideMessage() {
        $('#message').hide();
    }

    // ---------------------------------------------------------------- fields

    function fieldOption(field) {
        return '<option value="' + field.id + '">' + field.name + '</option>';
    }

    function isUserPickerType(type) {
        return type && type.indexOf('userpicker') >= 0;
    }

    function populateCoAssigneeSelect(config) {
        var $select = $('#coassignee-field');
        $select.find('option:not([value=""])').remove();
        state.fields.forEach(function (field) {
            if (isUserPickerType(field.type)) {
                $select.append(fieldOption(field));
            }
        });
        if (config && config.coAssigneeFieldId) {
            $select.val(config.coAssigneeFieldId);
        }
    }

    function buildOperatorOptions() {
        var html = '';
        Object.keys(OPERATOR_LABELS).forEach(function (key) {
            html += '<option value="' + key + '">' + OPERATOR_LABELS[key] + '</option>';
        });
        return html;
    }

    function buildFieldSelect() {
        var html = '<select class="select condition-field">';
        state.fields.forEach(function (field) {
            html += fieldOption(field);
        });
        html += '</select>';
        return html;
    }

    // ------------------------------------------------------------ conditions

    function addConditionRow(condition) {
        condition = condition || {};
        var $row = $(
            '<div class="condition-row">' +
            '  ' + buildFieldSelect() +
            '  <select class="select condition-operator">' + buildOperatorOptions() + '</select>' +
            '  <input class="text condition-value" type="text" placeholder="Значение">' +
            '  <button class="aui-button aui-button-light remove-condition" type="button">Удалить</button>' +
            '</div>'
        );

        if (condition.fieldId) {
            $row.find('.condition-field').val(condition.fieldId);
        }
        if (condition.operator) {
            $row.find('.condition-operator').val(condition.operator);
        }
        if (condition.value) {
            $row.find('.condition-value').val(condition.value);
        }
        applyOperatorState($row.find('.condition-operator'), $row.find('.condition-value'));

        $('#rule-conditions').append($row);
    }

    function applyOperatorState($operator, $value) {
        var op = $operator.val();
        if (op === 'IS_EMPTY' || op === 'IS_NOT_EMPTY') {
            $value.val('').prop('disabled', true);
        } else {
            $value.prop('disabled', false);
        }
    }

    function collectConditions() {
        var conditions = [];
        $('#rule-conditions .condition-row').each(function () {
            var $row = $(this);
            var fieldId = $row.find('.condition-field').val();
            var operator = $row.find('.condition-operator').val();
            var value = $row.find('.condition-value').val();
            if (!fieldId || !operator) {
                return;
            }
            conditions.push({ fieldId: fieldId, operator: operator, value: value || null });
        });
        return conditions;
    }

    // ----------------------------------------------------------------- users

    function populateTargetUsers(selectedNames) {
        var $select = $('#rule-target-users');
        $select.empty();
        state.users.forEach(function (user) {
            $select.append('<option value="' + user.name + '">' + user.displayName + ' (' + user.name + ')</option>');
        });
        if (selectedNames) {
            $select.val(selectedNames);
        }
    }

    function applyTargetTypeState() {
        var type = $('#rule-target-type').val();
        var $select = $('#rule-target-users');
        if (type === 'ASSIGNEE') {
            $select.prop('multiple', false).attr('size', 1);
        } else {
            $select.prop('multiple', true).attr('size', 5);
        }
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
            return fieldName + ' ' + op + ' «' + (c.value || '') + '»';
        }).join(' И ');
    }

    function renderRules(rules) {
        var $body = $('#rules-table-body');
        $body.empty();

        $('#no-rules').toggle(!rules || rules.length === 0);
        $('#rules-table').toggle(rules && rules.length > 0);

        (rules || []).forEach(function (rule) {
            var users = (rule.targetUsers || []).map(function (name) {
                var user = state.users.filter(function (u) { return u.name === name; })[0];
                return user ? user.displayName : name;
            }).join(', ');

            var $row = $(
                '<tr>' +
                '  <td></td>' +
                '  <td></td>' +
                '  <td></td>' +
                '  <td></td>' +
                '  <td></td>' +
                '  <td class="rule-actions"></td>' +
                '</tr>'
            );
            $row.find('td').eq(0).text(rule.name || '(без названия)');
            $row.find('td').eq(1).text(TARGET_LABELS[rule.targetType] || rule.targetType);
            $row.find('td').eq(2).text(users);
            $row.find('td').eq(3).text(conditionsSummary(rule));
            $row.find('td').eq(4).text(rule.enabled ? 'Да' : 'Нет');

            var $edit = $('<button class="aui-button aui-button-light edit-rule" type="button">Изменить</button>');
            var $delete = $('<button class="aui-button aui-button-light delete-rule" type="button">Удалить</button>');
            $edit.on('click', function () { openEditDialog(rule); });
            $delete.on('click', function () { deleteRule(rule); });
            $row.find('td').eq(5).append($edit).append(' ').append($delete);

            $body.append($row);
        });
    }

    // ---------------------------------------------------------------- dialog

    var ruleDialog = null;

    function openCreateDialog() {
        state.editingId = null;
        $('#rule-dialog-title').text('Добавить правило');
        $('#rule-form')[0].reset();
        $('#rule-target-type').val('ASSIGNEE');
        $('#rule-enabled').prop('checked', true);
        $('#rule-conditions').empty();
        populateTargetUsers([]);
        applyTargetTypeState();
        addConditionRow();
        ruleDialog.show();
    }

    function openEditDialog(rule) {
        state.editingId = rule.id;
        $('#rule-dialog-title').text('Изменить правило');
        $('#rule-form')[0].reset();
        $('#rule-name').val(rule.name);
        $('#rule-target-type').val(rule.targetType);
        $('#rule-enabled').prop('checked', rule.enabled);
        populateTargetUsers(rule.targetUsers || []);
        applyTargetTypeState();

        $('#rule-conditions').empty();
        if (rule.conditions && rule.conditions.length > 0) {
            rule.conditions.forEach(addConditionRow);
        } else {
            addConditionRow();
        }
        ruleDialog.show();
    }

    function collectRule() {
        var targetType = $('#rule-target-type').val();
        var targetUsers = $('#rule-target-users').val() || [];
        if (!Array.isArray(targetUsers)) {
            targetUsers = [targetUsers];
        }
        return {
            id: state.editingId,
            projectKey: CONFIG.projectKey,
            name: $('#rule-name').val(),
            targetType: targetType,
            targetUsers: targetUsers,
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
        if (!rule.targetUsers || rule.targetUsers.length === 0) {
            showMessage('error', 'Выберите хотя бы одного пользователя.');
            return;
        }

        var isUpdate = state.editingId != null;
        var options = isUpdate
            ? { method: 'PUT', url: restUrl('/rules/' + state.editingId), data: JSON.stringify(rule) }
            : { method: 'POST', url: restUrl('/rules'), data: JSON.stringify(rule) };

        request(options)
            .done(function () {
                ruleDialog.hide();
                showMessage('success', 'Правило сохранено.');
                reloadRules();
            })
            .fail(function (xhr) {
                showMessage('error', 'Не удалось сохранить правило: ' + (xhr.responseText || xhr.statusText || xhr.status));
            });
    }

    function deleteRule(rule) {
        if (!window.confirm('Удалить правило «' + (rule.name || 'без названия') + '»?')) {
            return;
        }
        hideMessage();
        request({ method: 'DELETE', url: restUrl('/rules/' + rule.id + '?projectKey=' + encodeURIComponent(CONFIG.projectKey)) })
            .done(function () {
                showMessage('success', 'Правило удалено.');
                reloadRules();
            })
            .fail(function (xhr) {
                showMessage('error', 'Не удалось удалить правило: ' + (xhr.responseText || xhr.statusText || xhr.status));
            });
    }

    function saveGlobalConfig() {
        hideMessage();
        var payload = {
            enabled: $('#enabled-toggle').prop('checked'),
            coAssigneeFieldId: $('#coassignee-field').val()
        };
        request({ method: 'PUT', url: restUrl('/config'), data: JSON.stringify(payload) })
            .done(function () {
                showMessage('success', 'Настройки сохранены.');
            })
            .fail(function (xhr) {
                showMessage('error', 'Не удалось сохранить настройки: ' + (xhr.responseText || xhr.statusText || xhr.status));
            });
    }

    // ---------------------------------------------------------------- loading

    function reloadRules() {
        if (!CONFIG.projectKey) {
            renderRules([]);
            return;
        }
        request({ url: restUrl('/rules?projectKey=' + encodeURIComponent(CONFIG.projectKey)) })
            .done(renderRules)
            .fail(function (xhr) {
                showMessage('error', 'Не удалось загрузить правила: ' + (xhr.responseText || xhr.statusText || xhr.status));
            });
    }

    function bindEvents() {
        ruleDialog = AJS.dialog2('#rule-dialog');

        $('#add-rule-btn').on('click', openCreateDialog);
        $('#rule-save-btn').on('click', saveRule);
        $('#rule-cancel-btn').on('click', function () { ruleDialog.hide(); });
        $('#save-config-btn').on('click', saveGlobalConfig);

        $('#add-condition-btn').on('click', function () { addConditionRow(); });

        $('#rule-target-type').on('change', applyTargetTypeState);

        $('#rule-conditions').on('change', '.condition-operator', function () {
            var $row = $(this).closest('.condition-row');
            applyOperatorState($(this), $row.find('.condition-value'));
        });

        $('#rule-conditions').on('click', '.remove-condition', function () {
            $(this).closest('.condition-row').remove();
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

        $.when(
            getData(restUrl('/fields')),
            getData(restUrl('/users')),
            getData(restUrl('/config')),
            getData(restUrl('/rules?projectKey=' + encodeURIComponent(CONFIG.projectKey)))
        ).done(function (fields, users, config, rules) {
            state.fields = fields || [];
            state.users = users || [];

            $('#enabled-toggle').prop('checked', config && config.enabled);
            populateCoAssigneeSelect(config);
            renderRules(rules);
        }).fail(function (xhr) {
            showMessage('error', 'Не удалось загрузить конфигурацию: ' + (xhr.responseText || xhr.statusText || xhr.status));
        });
    }

    $(document).ready(init);
})(AJS);
