(function() {
    "use strict";

    var AUTO_DISMISS_DELAY = 5500;
    var DISMISS_TRANSITION_DELAY = 180;

    function syncRegionPosition(region) {
        var topbar = document.querySelector("[data-ili-topbar]");
        if (!topbar) {
            return;
        }
        var gap = window.matchMedia && window.matchMedia("(max-width: 768px)").matches
            ? 8
            : 12;
        region.style.setProperty(
            "--ili-notification-top",
            Math.ceil(topbar.getBoundingClientRect().height + gap) + "px"
        );
    }

    function initNotification(notification) {
        var dismissButton = notification.querySelector("[data-notification-dismiss]");
        var timer = null;
        var dismissed = false;

        function dismiss() {
            if (dismissed) {
                return;
            }
            dismissed = true;
            if (timer !== null) {
                window.clearTimeout(timer);
            }
            notification.classList.add("is-dismissing");
            window.setTimeout(function() {
                notification.hidden = true;
                notification.remove();
            }, DISMISS_TRANSITION_DELAY);
        }

        if (dismissButton) {
            dismissButton.addEventListener("click", dismiss);
        }

        var level = notification.getAttribute("data-notification-level");
        if (level === "success" || level === "info") {
            timer = window.setTimeout(dismiss, AUTO_DISMISS_DELAY);
        }
    }

    document.addEventListener("DOMContentLoaded", function() {
        var region = document.querySelector("[data-ili-notifications]");
        if (region) {
            syncRegionPosition(region);
            window.addEventListener("resize", function() {
                syncRegionPosition(region);
            });
            if (typeof ResizeObserver === "function") {
                var topbar = document.querySelector("[data-ili-topbar]");
                if (topbar) {
                    new ResizeObserver(function() {
                        syncRegionPosition(region);
                    }).observe(topbar);
                }
            }
        }
        document.querySelectorAll("[data-ili-notification]").forEach(initNotification);
    });
})();
