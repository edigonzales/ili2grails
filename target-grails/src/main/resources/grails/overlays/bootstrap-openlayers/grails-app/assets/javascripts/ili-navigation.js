(function() {
    "use strict";

    function uiMessage(name, fallback) {
        var body = document.body;
        return body && body.getAttribute("data-ili-message-" + name) || fallback;
    }

    var FAVORITES_KEY = "ili2grails.ui.favorites";
    var RECENTS_KEY = "ili2grails.ui.recents";
    var RECENTS_LIMIT = 8;

    function safeStorage() {
        try {
            return window.localStorage;
        } catch (error) {
            return null;
        }
    }

    function readKeys(storage, key) {
        if (!storage) {
            return [];
        }
        try {
            var raw = storage.getItem(key);
            var parsed = raw ? JSON.parse(raw) : [];
            if (!Array.isArray(parsed)) {
                return [];
            }
            return parsed.filter(function(value) {
                return typeof value === "string" && value.trim().length > 0;
            });
        } catch (error) {
            return [];
        }
    }

    function writeKeys(storage, key, values) {
        if (!storage) {
            return;
        }
        try {
            storage.setItem(key, JSON.stringify(values));
        } catch (error) {
            // Private browsing and storage quotas must not break navigation.
        }
    }

    function domainCatalog() {
        var catalog = {};
        document.querySelectorAll("[data-ili-domain-entry][data-ili-domain-key]")
            .forEach(function(entry) {
                var key = entry.getAttribute("data-ili-domain-key");
                if (!key || catalog[key]) {
                    return;
                }
                var link = entry.querySelector("[data-ili-domain-link]");
                if (!link) {
                    return;
                }
                catalog[key] = {
                    key: key,
                    label: entry.getAttribute("data-ili-domain-label") || key,
                    className: entry.getAttribute("data-ili-domain-class") || "",
                    topic: entry.getAttribute("data-ili-domain-topic") || "",
                    model: entry.getAttribute("data-ili-domain-model") || "",
                    iliName: entry.getAttribute("data-ili-domain-ili-name") || key,
                    url: link.getAttribute("data-ili-domain-url") || link.href
                };
            });
        return catalog;
    }

    function makeLocalDomainLink(domain) {
        var item = document.createElement("li");
        item.className = "ili-domain-list-item";
        var link = document.createElement("a");
        link.className = "ili-domain-link";
        link.href = domain.url;
        link.setAttribute("data-ili-domain-link", "true");
        link.setAttribute("data-ili-domain-key", domain.key);
        link.setAttribute("data-ili-domain-url", domain.url);
        var label = document.createElement("span");
        label.className = "ili-domain-link-label";
        label.textContent = domain.label;
        link.appendChild(label);
        item.appendChild(link);
        return item;
    }

    function renderLocalList(list, keys, catalog) {
        if (!list) {
            return;
        }
        while (list.firstChild) {
            list.removeChild(list.firstChild);
        }
        keys.forEach(function(key) {
            if (catalog[key]) {
                list.appendChild(makeLocalDomainLink(catalog[key]));
            }
        });
    }

    function updateLocalSections(favorites, recents, catalog) {
        document.querySelectorAll('[data-ili-local-section="favorites"]').forEach(function(section) {
            renderLocalList(section.querySelector("[data-ili-favorites-list]"), favorites, catalog);
            section.hidden = favorites.length === 0;
        });
        document.querySelectorAll('[data-ili-local-section="recents"]').forEach(function(section) {
            renderLocalList(section.querySelector("[data-ili-recents-list]"), recents, catalog);
            section.hidden = recents.length === 0;
        });
    }

    function updateFavoriteButtons(favorites) {
        document.querySelectorAll("[data-ili-favorite-toggle]").forEach(function(button) {
            var key = button.getAttribute("data-ili-domain-key");
            var selected = favorites.indexOf(key) >= 0;
            var label = selected
                ? uiMessage("favorite-remove", "Favorit entfernen")
                : uiMessage("favorite-mark", "Als Favorit markieren");
            button.setAttribute("aria-pressed", selected ? "true" : "false");
            button.setAttribute("aria-label", label);
            button.setAttribute("title", label);
            button.classList.toggle("is-favorite", selected);
        });
    }

    function initLocalDomainState() {
        var storage = safeStorage();
        var catalog = domainCatalog();
        if (!storage) {
            document.querySelectorAll("[data-ili-favorite-toggle]").forEach(function(button) {
                button.hidden = true;
            });
            document.querySelectorAll("[data-ili-local-section]").forEach(function(section) {
                section.hidden = true;
            });
            return;
        }

        var favorites = readKeys(storage, FAVORITES_KEY);
        var recents = readKeys(storage, RECENTS_KEY).slice(0, RECENTS_LIMIT);

        function refresh() {
            updateLocalSections(favorites, recents, catalog);
            updateFavoriteButtons(favorites);
        }

        document.addEventListener("click", function(event) {
            var favoriteButton = event.target.closest("[data-ili-favorite-toggle]");
            if (favoriteButton) {
                event.preventDefault();
                event.stopPropagation();
                var key = favoriteButton.getAttribute("data-ili-domain-key");
                var index = favorites.indexOf(key);
                if (index >= 0) {
                    favorites.splice(index, 1);
                } else if (key) {
                    favorites.push(key);
                }
                writeKeys(storage, FAVORITES_KEY, favorites);
                refresh();
                return;
            }

            var domainLink = event.target.closest("[data-ili-domain-link]");
            if (!domainLink) {
                return;
            }
            var domainKey = domainLink.getAttribute("data-ili-domain-key");
            if (!domainKey || !catalog[domainKey]) {
                return;
            }
            recents = [domainKey].concat(recents.filter(function(value) {
                return value !== domainKey;
            })).slice(0, RECENTS_LIMIT);
            writeKeys(storage, RECENTS_KEY, recents);
        });

        refresh();
    }

    function finderText(domain) {
        return [domain.label, domain.className, domain.topic, domain.model, domain.iliName]
            .join(" ").toLocaleLowerCase();
    }

    function initDomainFinder() {
        var form = document.querySelector("[data-ili-domain-finder-form]");
        var input = document.querySelector("[data-ili-domain-finder-input]");
        var results = document.querySelector("[data-ili-finder-results]");
        if (!form || !input || !results) {
            return;
        }

        var catalog = domainCatalog();
        var domains = Object.keys(catalog).map(function(key) {
            return catalog[key];
        });
        var activeIndex = -1;

        function closeResults() {
            results.hidden = true;
            input.setAttribute("aria-expanded", "false");
            input.removeAttribute("aria-activedescendant");
            activeIndex = -1;
        }

        function setActive(index) {
            var options = Array.from(results.querySelectorAll("[role='option']"));
            if (!options.length) {
                activeIndex = -1;
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

        function renderResults() {
            var query = (input.value || "").trim().toLocaleLowerCase();
            while (results.firstChild) {
                results.removeChild(results.firstChild);
            }
            activeIndex = -1;
            if (!query) {
                closeResults();
                return;
            }
            var matches = domains.filter(function(domain) {
                return finderText(domain).indexOf(query) >= 0;
            }).slice(0, 12);
            if (!matches.length) {
                var empty = document.createElement("div");
                empty.className = "list-group-item text-body-secondary";
                empty.textContent = uiMessage("no-domain", "Keine Domain gefunden");
                results.appendChild(empty);
            } else {
                matches.forEach(function(domain, optionIndex) {
                    var option = document.createElement("a");
                    option.className = "list-group-item list-group-item-action";
                    option.id = "ili-finder-option-" + optionIndex;
                    option.href = domain.url;
                    option.setAttribute("role", "option");
                    option.setAttribute("aria-selected", "false");
                    option.setAttribute("data-ili-domain-link", "true");
                    option.setAttribute("data-ili-domain-key", domain.key);
                    option.setAttribute("data-ili-domain-url", domain.url);
                    option.textContent = domain.label +
                        (domain.topic ? " · " + domain.topic : "");
                    results.appendChild(option);
                });
            }
            results.hidden = false;
            input.setAttribute("aria-expanded", "true");
        }

        input.addEventListener("input", renderResults);
        input.addEventListener("keydown", function(event) {
            var options = results.querySelectorAll("[role='option']");
            if (event.key === "ArrowDown" && options.length) {
                event.preventDefault();
                setActive(activeIndex + 1);
            } else if (event.key === "ArrowUp" && options.length) {
                event.preventDefault();
                setActive(activeIndex - 1);
            } else if (event.key === "Escape") {
                closeResults();
            }
        });

        form.addEventListener("submit", function(event) {
            var active = results.querySelectorAll("[role='option']")[activeIndex];
            if (active && !results.hidden) {
                event.preventDefault();
                window.location.assign(active.href);
            }
        });

        document.addEventListener("click", function(event) {
            if (!form.contains(event.target)) {
                closeResults();
            }
        });
    }

    function initListPageSize() {
        document.querySelectorAll("[data-ili-page-size-select]").forEach(function(select) {
            select.addEventListener("change", function() {
                if (select.form) {
                    select.form.submit();
                }
            });
        });
    }

    document.addEventListener("DOMContentLoaded", function() {
        initDomainFinder();
        initLocalDomainState();
        initListPageSize();
    });
})();
