(function() {
    "use strict";

    var dirtyForms = new Set();

    function readValue(field) {
        if (!field) {
            return "";
        }
        var tag = field.tagName.toLowerCase();
        if (tag === "input") {
            var type = (field.getAttribute("type") || "text").toLowerCase();
            if (type === "checkbox" || type === "radio") {
                return field.checked ? "1" : "0";
            }
        }
        return field.value || "";
    }

    function setDirtyState(form, dirty) {
        if (dirty) {
            dirtyForms.add(form);
        } else {
            dirtyForms.delete(form);
        }
        form.dataset.dirty = dirty ? "true" : "false";

        var page = form.closest(".ili-page");
        var badge = page ? page.querySelector("[data-unsaved-badge]") : null;
        if (badge) {
            badge.hidden = !dirty;
        }
    }

    function evaluateDirty(form, initialValues, trackedFields) {
        var dirty = trackedFields.some(function(field) {
            return readValue(field) !== initialValues.get(field);
        });
        setDirtyState(form, dirty);
    }

    function initDirtyForm(form) {
        var trackedFields = Array.from(form.querySelectorAll("input, select, textarea"))
            .filter(function(field) {
                if (!field.name && !field.classList.contains("js-geometry-wkt")) {
                    return false;
                }
                var type = (field.getAttribute("type") || "").toLowerCase();
                return type !== "submit" && type !== "button";
            });

        if (!trackedFields.length) {
            return;
        }

        var initialValues = new Map();
        trackedFields.forEach(function(field) {
            initialValues.set(field, readValue(field));
            field.addEventListener("input", function() {
                evaluateDirty(form, initialValues, trackedFields);
            });
            field.addEventListener("change", function() {
                evaluateDirty(form, initialValues, trackedFields);
            });
        });

        form.addEventListener("submit", function() {
            setDirtyState(form, false);
        });

        evaluateDirty(form, initialValues, trackedFields);
    }

    function hasDirtyForm() {
        return dirtyForms.size > 0;
    }

    function initBeforeUnloadGuard() {
        window.addEventListener("beforeunload", function(event) {
            if (!hasDirtyForm()) {
                return;
            }
            event.preventDefault();
            event.returnValue = "";
        });
    }

    function submitFormFromAction(event, actionElement) {
        var form = actionElement.closest("form");
        if (!form) {
            return;
        }
        var actionType = (actionElement.getAttribute("type") || "").toLowerCase();
        if (actionType === "submit" && actionElement.name && actionElement.form === form) {
            // Keep the browser submitter. Its name/value carries the explicit
            // save mode and native constraint validation must run first.
            return;
        }
        var nativeSubmit = form.querySelector(".js-native-submit");
        event.preventDefault();
        if (nativeSubmit) {
            nativeSubmit.click();
            return;
        }
        if (typeof form.requestSubmit === "function") {
            form.requestSubmit();
            return;
        }
        form.submit();
    }

    function submitNativeForm(form, submitSelector) {
        if (!form) {
            return;
        }
        var submit = submitSelector ? form.querySelector(submitSelector) : null;
        if (typeof form.requestSubmit === "function") {
            form.requestSubmit(submit || undefined);
            return;
        }
        if (submit) {
            submit.click();
            return;
        }
        form.submit();
    }

    function findForm(formId) {
        if (!formId) {
            return null;
        }
        return document.getElementById(formId)
            || document.querySelector("form[name='" + CSS.escape(formId) + "']");
    }

    function initSubmitButtons() {
        document.addEventListener("click", function(event) {
            var submitAction = event.target.closest("[data-form-submit]");
            if (submitAction) {
                submitFormFromAction(event, submitAction);
                return;
            }

            var rowDeleteAction = event.target.closest("[data-row-delete]");
            if (rowDeleteAction) {
                event.preventDefault();
                if (!window.confirm("Datensatz wirklich löschen?")) {
                    return;
                }
                var rowDeleteFormId = rowDeleteAction.getAttribute("data-delete-form");
                var rowDeleteForm = findForm(rowDeleteFormId);
                if (!rowDeleteForm) {
                    return;
                }
                submitNativeForm(rowDeleteForm, ".js-delete-submit");
            }

            var associationDeleteAction = event.target.closest("[data-association-delete]");
            if (associationDeleteAction) {
                event.preventDefault();
                if (!window.confirm("Zuordnung wirklich entfernen?")) {
                    return;
                }
                var associationFormId = associationDeleteAction.getAttribute("data-delete-form");
                var associationForm = associationFormId
                    ? document.querySelector("form[name='" + associationFormId + "']")
                    : null;
                if (!associationForm) {
                    return;
                }
                submitNativeForm(associationForm, ".js-delete-submit");
            }
        });
    }

    function initUnsavedNavigationGuard() {
        document.addEventListener("click", function(event) {
            var navAction = event.target.closest("a[href]");
            if (!navAction || !hasDirtyForm()) {
                return;
            }
            if (navAction.getAttribute("data-unsaved-bypass") === "true"
                || navAction.getAttribute("data-unsaved-nav") === "false"
                || navAction.hasAttribute("download")
                || navAction.target && navAction.target !== "_self"
                || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
                return;
            }
            var href = navAction.getAttribute("href") || "";
            if (!href || href.charAt(0) === "#") {
                return;
            }
            var proceed = window.confirm("Es gibt ungespeicherte Änderungen. Seite wirklich verlassen?");
            if (!proceed) {
                event.preventDefault();
                event.stopPropagation();
            }
        });
    }

    function hideModal(modalElement) {
        if (!modalElement || !window.bootstrap || !bootstrap.Modal) {
            return;
        }
        var modal = bootstrap.Modal.getInstance(modalElement) || bootstrap.Modal.getOrCreateInstance(modalElement);
        modal.hide();
    }

    function initDeleteModal() {
        document.addEventListener("show.bs.modal", function(event) {
            var modal = event.target.closest ? event.target.closest("[data-delete-modal]") : null;
            if (!modal || !event.relatedTarget) {
                return;
            }
            modal._iliReturnFocus = event.relatedTarget;
        });

        document.addEventListener("hidden.bs.modal", function(event) {
            var modal = event.target.closest ? event.target.closest("[data-delete-modal]") : null;
            var returnFocus = modal && modal._iliReturnFocus;
            if (!returnFocus || !document.contains(returnFocus)) {
                return;
            }
            window.setTimeout(function() {
                returnFocus.focus();
            }, 0);
        });

        document.addEventListener("click", function(event) {
            var modalTrigger = event.target.closest("[data-delete-open]");
            if (modalTrigger) {
                var modalId = modalTrigger.getAttribute("data-delete-open");
                var modal = modalId ? document.getElementById(modalId) : null;
                if (modal) {
                    modal._iliReturnFocus = modalTrigger;
                }
            }

            var confirmAction = event.target.closest("[data-delete-confirm]");
            if (!confirmAction) {
                return;
            }
            event.preventDefault();
            var formId = confirmAction.getAttribute("data-delete-form");
            var form = findForm(formId);
            hideModal(confirmAction.closest(".modal"));
            if (!form) {
                return;
            }
            submitNativeForm(form, ".js-delete-submit");
        });
    }

    function optionFromData(item) {
        var option = document.createElement("option");
        option.value = item.id || "";
        option.textContent = item.label || item.id || "";
        return option;
    }

    function selectedOptionLabel(select) {
        var selectedOption = select && select.selectedOptions && select.selectedOptions.length ? select.selectedOptions[0] : null;
        return selectedOption ? selectedOption.textContent || "" : "";
    }

    function appendRelationshipOptions(select, results, reset) {
        if (!select) {
            return;
        }
        var previousValue = select.value;
        var previousLabel = selectedOptionLabel(select);
        var optional = select.getAttribute("data-relationship-optional") !== "false";
        if (reset) {
            select.innerHTML = "";
        }
        if (reset && optional) {
            var empty = document.createElement("option");
            empty.value = "";
            empty.textContent = "Keine Auswahl";
            select.appendChild(empty);
        }
        var hasPreviousValue = !previousValue;
        var knownValues = new Set(Array.from(select.options).map(function(option) {
            return option.value;
        }));
        (results || []).forEach(function(item) {
            if (!item || !item.id) {
                return;
            }
            if (item.id === previousValue) {
                hasPreviousValue = true;
            }
            if (knownValues.has(item.id)) {
                return;
            }
            select.appendChild(optionFromData(item));
            knownValues.add(item.id);
        });
        if (previousValue && !hasPreviousValue) {
            select.appendChild(optionFromData({
                id: previousValue,
                label: previousLabel || previousValue
            }));
        }
        select.value = previousValue || "";
    }

    function renderRelationshipList(list, results, select, reset) {
        if (!list) {
            return;
        }
        if (reset) {
            list.innerHTML = "";
        }
        (results || []).forEach(function(item) {
            if (!item || !item.id) {
                return;
            }
            var duplicate = Array.from(list.querySelectorAll("[data-relationship-value]")).some(function(option) {
                return option.getAttribute("data-relationship-value") === item.id;
            });
            if (duplicate) {
                return;
            }
            var option = document.createElement("button");
            option.type = "button";
            option.className = "list-group-item list-group-item-action ili-relationship-result";
            option.id = (list.id || "ili-relationship-results") + "-option-" + list.children.length;
            option.setAttribute("role", "option");
            option.setAttribute("data-relationship-value", item.id);
            option.setAttribute("data-relationship-label", item.label || item.id);
            option.textContent = item.label || item.id;
            if (select && select.value === item.id) {
                option.classList.add("active");
                option.setAttribute("aria-selected", "true");
            } else {
                option.setAttribute("aria-selected", "false");
            }
            list.appendChild(option);
        });
        list.hidden = list.children.length === 0;
    }

    function markRelationshipSelection(list, value) {
        if (!list) {
            return;
        }
        Array.from(list.querySelectorAll("[data-relationship-value]")).forEach(function(option) {
            var active = option.getAttribute("data-relationship-value") === value;
            option.classList.toggle("active", active);
            option.setAttribute("aria-selected", active ? "true" : "false");
        });
    }

    function relationshipUrl(input, offset) {
        var url = input.getAttribute("data-relationship-url");
        if (!url) {
            return null;
        }
        var field = input.getAttribute("data-relationship-field");
        var context = input.getAttribute("data-relationship-context");
        var role = input.getAttribute("data-relationship-role");
        if (!field && !context) {
            return null;
        }
        var params = new URLSearchParams();
        if (field) {
            params.set("field", field);
        }
        if (context) {
            params.set("context", context);
        }
        if (role) {
            params.set("role", role);
        }
        params.set("q", input.value || "");
        params.set("max", "25");
        params.set("offset", String(offset || 0));
        return url + (url.indexOf("?") >= 0 ? "&" : "?") + params.toString();
    }

    function initRelationshipAutocomplete(input) {
        var selectId = input.getAttribute("data-relationship-select");
        var select = selectId ? document.getElementById(selectId) : null;
        if (!select || typeof window.fetch !== "function") {
            return;
        }
        var picker = input.closest(".js-relationship-picker");
        var list = picker ? picker.querySelector("[data-relationship-list]") : null;
        var state = {
            loading: false,
            loaded: false,
            more: false,
            offset: 0,
            controller: null
        };
        var timer = null;
        var activeIndex = -1;

        function closeResults() {
            if (list) {
                list.hidden = true;
            }
            input.setAttribute("aria-expanded", "false");
            input.removeAttribute("aria-activedescendant");
            activeIndex = -1;
        }

        function setActive(index) {
            if (!list) {
                return;
            }
            var options = Array.from(list.querySelectorAll("[role='option']"));
            if (!options.length) {
                closeResults();
                return;
            }
            activeIndex = (index + options.length) % options.length;
            options.forEach(function(option, optionIndex) {
                var active = optionIndex === activeIndex;
                option.classList.toggle("active", active);
                option.setAttribute("aria-selected", active ? "true" : "false");
            });
            input.setAttribute("aria-activedescendant", options[activeIndex].id);
            options[activeIndex].scrollIntoView({ block: "nearest" });
        }

        function fetchOptions(reset) {
            if (state.loading) {
                if (reset && state.controller) {
                    state.controller.abort();
                } else {
                    return;
                }
            }
            if (reset) {
                state.offset = 0;
                state.more = false;
            } else if (!state.more) {
                return;
            }
            var url = relationshipUrl(input, state.offset);
            if (!url) {
                return;
            }
            var controller = typeof AbortController === "function" ? new AbortController() : null;
            state.controller = controller;
            state.loading = true;
            window.fetch(url, {
                headers: {
                    "Accept": "application/json"
                },
                signal: controller ? controller.signal : undefined
            })
                .then(function(response) {
                    return response.ok ? response.json() : null;
                })
                .then(function(payload) {
                    if (!payload) {
                        return;
                    }
                    var results = payload.results || [];
                    var pagination = payload.pagination || {};
                    appendRelationshipOptions(select, results, reset);
                    renderRelationshipList(list, results, select, reset);
                    if (list && !list.hidden) {
                        input.setAttribute("aria-expanded", "true");
                    } else {
                        closeResults();
                    }
                    activeIndex = -1;
                    input.removeAttribute("aria-activedescendant");
                    state.loaded = true;
                    state.more = pagination.more === true;
                    var nextOffset = parseInt(pagination.nextOffset, 10);
                    state.offset = Number.isFinite(nextOffset) ? nextOffset : state.offset + results.length;
                })
                .catch(function(error) {
                    // Keep the existing server-rendered options if autocomplete fails or is aborted.
                    if (error && error.name === "AbortError") {
                        return;
                    }
                })
                .finally(function() {
                    state.loading = false;
                });
        }

        input.addEventListener("input", function() {
            window.clearTimeout(timer);
            timer = window.setTimeout(function() {
                fetchOptions(true);
            }, 250);
        });

        input.addEventListener("focus", function() {
            if (!state.loaded) {
                fetchOptions(true);
            } else if (list && list.children.length) {
                list.hidden = false;
                input.setAttribute("aria-expanded", "true");
            }
        });

        input.addEventListener("keydown", function(event) {
            var options = list ? list.querySelectorAll("[role='option']") : [];
            if (event.key === "ArrowDown" && options.length) {
                event.preventDefault();
                setActive(activeIndex + 1);
            } else if (event.key === "ArrowUp" && options.length) {
                event.preventDefault();
                setActive(activeIndex - 1);
            } else if (event.key === "Enter" && activeIndex >= 0 && options[activeIndex]) {
                event.preventDefault();
                options[activeIndex].click();
            } else if (event.key === "Escape") {
                closeResults();
            }
        });

        select.addEventListener("change", function() {
            input.value = selectedOptionLabel(select);
            markRelationshipSelection(list, select.value);
        });

        if (list) {
            list.addEventListener("click", function(event) {
                var option = event.target.closest("[data-relationship-value]");
                if (!option) {
                    return;
                }
                select.value = option.getAttribute("data-relationship-value") || "";
                input.value = option.getAttribute("data-relationship-label") || "";
                markRelationshipSelection(list, select.value);
                select.dispatchEvent(new Event("change", { bubbles: true }));
                closeResults();
            });

            list.addEventListener("scroll", function() {
                if (list.scrollTop + list.clientHeight >= list.scrollHeight - 24) {
                    fetchOptions(false);
                }
            });
        }
    }

    function initQuickAddForms() {
        document.querySelectorAll(".ili-association-quick-form").forEach(function(form) {
            var select = form.querySelector("select[name='targetId']");
            var submit = form.querySelector("[data-quick-add-submit]");
            if (!select || !submit) {
                return;
            }
            function syncSubmitState() {
                submit.disabled = !select.value;
            }
            select.addEventListener("change", syncSubmitState);
            form.addEventListener("submit", function(event) {
                if (!select.value) {
                    event.preventDefault();
                }
            });
            syncSubmitState();
        });
    }

    document.addEventListener("DOMContentLoaded", function() {
        document.querySelectorAll(".js-dirty-form").forEach(initDirtyForm);
        document.querySelectorAll(".js-relationship-search").forEach(initRelationshipAutocomplete);
        initBeforeUnloadGuard();
        initSubmitButtons();
        initUnsavedNavigationGuard();
        initDeleteModal();
        initQuickAddForms();
    });
})();
