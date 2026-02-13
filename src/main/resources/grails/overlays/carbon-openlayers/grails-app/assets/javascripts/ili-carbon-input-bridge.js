(function() {
    "use strict";

    function cssEscape(value) {
        if (!value) {
            return "";
        }
        if (window.CSS && typeof window.CSS.escape === "function") {
            return window.CSS.escape(value);
        }
        return value.replace(/([ #;?%&,.+*~':"!^$\[\]()=>|/@])/g, "\\$1");
    }

    function findLabel(root, field) {
        if (field.id) {
            var byFor = root.querySelector('label[for="' + cssEscape(field.id) + '"]');
            if (byFor) {
                return byFor;
            }
        }
        var row = field.closest(".ili-field-row");
        if (!row) {
            return null;
        }
        return row.querySelector("label");
    }

    function labelText(label, fallback) {
        if (!label) {
            return fallback;
        }
        return (label.textContent || fallback || "").replace(/\*/g, "").trim();
    }

    function setBooleanAttribute(element, attribute, enabled) {
        if (enabled) {
            element.setAttribute(attribute, "");
        } else {
            element.removeAttribute(attribute);
        }
    }

    function dispatchNativeEvents(field) {
        field.dispatchEvent(new Event("input", { bubbles: true }));
        field.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function syncComponentToNative(component, field) {
        var tagName = component.tagName.toLowerCase();
        if (tagName === "bx-checkbox") {
            field.checked = !!component.checked;
            dispatchNativeEvents(field);
            return;
        }
        field.value = component.value || "";
        dispatchNativeEvents(field);
    }

    function attachSyncListeners(component, field) {
        ["input", "change", "bx-input", "bx-number-input", "bx-select-selected", "bx-checkbox-changed"].forEach(function(eventName) {
            component.addEventListener(eventName, function() {
                syncComponentToNative(component, field);
            });
        });
    }

    function configureCommonAttributes(component, field, caption) {
        if (caption) {
            component.setAttribute("label-text", caption);
        }
        if (field.name) {
            component.setAttribute("name", field.name + "__carbon");
        }
        if (field.placeholder && component.tagName.toLowerCase() !== "bx-checkbox") {
            component.setAttribute("placeholder", field.placeholder);
        }
        setBooleanAttribute(component, "required", !!field.required);
        setBooleanAttribute(component, "disabled", !!field.disabled);
        setBooleanAttribute(component, "readonly", !!field.readOnly);
    }

    function buildSelect(field, caption) {
        var component = document.createElement("bx-select");
        configureCommonAttributes(component, field, caption);

        Array.from(field.options || []).forEach(function(option) {
            var item = document.createElement("bx-select-item");
            item.setAttribute("value", option.value);
            item.textContent = option.textContent || option.label || option.value;
            setBooleanAttribute(item, "disabled", !!option.disabled);
            setBooleanAttribute(item, "selected", !!option.selected);
            component.appendChild(item);
        });

        component.value = field.value || "";
        return component;
    }

    function buildCheckbox(field, caption) {
        var component = document.createElement("bx-checkbox");
        configureCommonAttributes(component, field, caption);
        component.checked = !!field.checked;
        if (field.value) {
            component.setAttribute("value", field.value);
        }
        return component;
    }

    function buildTextarea(field, caption) {
        var component = document.createElement("bx-textarea");
        configureCommonAttributes(component, field, caption);
        component.value = field.value || "";
        return component;
    }

    function buildNumberInput(field, caption) {
        var component = document.createElement("bx-number-input");
        configureCommonAttributes(component, field, caption);
        component.value = field.value || "";
        if (field.min) {
            component.setAttribute("min", field.min);
        }
        if (field.max) {
            component.setAttribute("max", field.max);
        }
        if (field.step) {
            component.setAttribute("step", field.step);
        }
        return component;
    }

    function buildInput(field, caption) {
        var component = document.createElement("bx-input");
        configureCommonAttributes(component, field, caption);
        component.setAttribute("type", field.getAttribute("type") || "text");
        component.value = field.value || "";
        return component;
    }

    function buildComponent(field, caption) {
        var tag = field.tagName.toLowerCase();
        if (tag === "select") {
            return buildSelect(field, caption);
        }
        if (tag === "textarea") {
            return buildTextarea(field, caption);
        }
        if (tag === "input") {
            var type = (field.getAttribute("type") || "text").toLowerCase();
            if (type === "checkbox") {
                return buildCheckbox(field, caption);
            }
            if (type === "number") {
                return buildNumberInput(field, caption);
            }
            return buildInput(field, caption);
        }
        return null;
    }

    function hideNativeElements(field, label) {
        field.classList.add("ili-native-field-hidden");
        field.setAttribute("tabindex", "-1");
        if (label) {
            label.classList.add("ili-native-label-hidden");
        }
    }

    function enhanceField(root, field) {
        if (field.classList.contains("ili-native-field-hidden")) {
            return;
        }
        var type = (field.getAttribute("type") || "").toLowerCase();
        if (type === "hidden" || type === "submit" || type === "button" || type === "file") {
            return;
        }

        var label = findLabel(root, field);
        var caption = labelText(label, field.name || "Wert");
        var component = buildComponent(field, caption);
        if (!component) {
            return;
        }

        var host = document.createElement("div");
        host.className = "ili-carbon-field-host";
        field.insertAdjacentElement("afterend", host);
        host.appendChild(component);

        hideNativeElements(field, label);
        attachSyncListeners(component, field);
        syncComponentToNative(component, field);
    }

    function initBridge() {
        if (!customElements.get("bx-input")) {
            return;
        }
        var roots = document.querySelectorAll(".js-carbon-bridge");
        roots.forEach(function(root) {
            var fields = root.querySelectorAll("input, select, textarea");
            fields.forEach(function(field) {
                enhanceField(root, field);
            });
        });
    }

    document.addEventListener("DOMContentLoaded", function() {
        window.setTimeout(initBridge, 50);
    });
})();
