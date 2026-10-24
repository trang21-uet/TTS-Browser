/*
 * Manual content-region picker. Installs window.__ttsPicker and starts picking:
 * taps are swallowed and the tapped element gets an orange outline. Kotlin drives it
 * through evaluateJavascript (see ElementPicker.kt): status(), parent(), selector(), stop().
 */
(function () {
  if (window.__ttsPicker) {
    window.__ttsPicker.start();
    return true;
  }
  var ATTR = "data-tts-picked";
  var STYLE_ID = "__tts_picker_style";
  var picker = { el: null, active: false };

  function mark(el) {
    if (picker.el) picker.el.removeAttribute(ATTR);
    picker.el = el;
    if (el) el.setAttribute(ATTR, "");
  }

  function onClick(event) {
    if (!picker.active) return;
    event.preventDefault();
    event.stopPropagation();
    var target = event.target;
    if (target && target.nodeType !== 1) target = target.parentElement;
    if (target && target !== document.documentElement) mark(target);
  }

  function escape(value) {
    return window.CSS && CSS.escape ? CSS.escape(value) : value.replace(/[^a-zA-Z0-9_-]/g, "\\$&");
  }

  // Ids/classes with long digit runs are usually per-page (post-123456) and would not match other chapters.
  function stable(token) {
    return token && !/\d{3,}/.test(token);
  }

  function selectorFor(el) {
    var parts = [];
    for (var node = el; node && node.nodeType === 1 && node !== document.documentElement; node = node.parentElement) {
      if (node === document.body) {
        parts.unshift("body");
        break;
      }
      if (stable(node.id) && document.querySelectorAll("#" + escape(node.id)).length === 1) {
        parts.unshift("#" + escape(node.id));
        break;
      }
      var part = node.nodeName.toLowerCase();
      (node.getAttribute("class") || "").split(/\s+/).filter(stable).slice(0, 2).forEach(function (c) {
        part += "." + escape(c);
      });
      var parent = node.parentElement;
      if (parent) {
        var sameTag = Array.prototype.filter.call(parent.children, function (s) {
          return s.nodeName === node.nodeName;
        });
        if (sameTag.length > 1) part += ":nth-of-type(" + (sameTag.indexOf(node) + 1) + ")";
      }
      parts.unshift(part);
    }
    return parts.join(" > ");
  }

  picker.start = function () {
    if (picker.active) return;
    picker.active = true;
    if (!document.getElementById(STYLE_ID)) {
      var style = document.createElement("style");
      style.id = STYLE_ID;
      style.textContent = "[" + ATTR + "]{outline:3px solid #ff9800 !important;outline-offset:-3px !important;" +
        "background-color:rgba(255,152,0,.12) !important}";
      (document.head || document.documentElement).appendChild(style);
    }
    window.addEventListener("click", onClick, true);
  };

  picker.stop = function () {
    picker.active = false;
    mark(null);
    window.removeEventListener("click", onClick, true);
    var style = document.getElementById(STYLE_ID);
    if (style) style.remove();
    return true;
  };

  // {active, picked: {label, textLength} | null}. Kotlin treats a missing picker (new document) as gone.
  picker.status = function () {
    var el = picker.el;
    var picked = null;
    if (el) {
      var label = el.nodeName.toLowerCase() + (el.id ? "#" + el.id : "");
      var cls = (el.getAttribute("class") || "").split(/\s+/).filter(Boolean).slice(0, 2);
      if (cls.length) label += "." + cls.join(".");
      picked = { label: label, textLength: (el.textContent || "").replace(/\s+/g, " ").trim().length };
    }
    return JSON.stringify({ active: picker.active, picked: picked });
  };

  picker.parent = function () {
    var el = picker.el;
    if (el && el.parentElement && el !== document.body && el.parentElement !== document.documentElement) {
      mark(el.parentElement);
    }
    return picker.status();
  };

  picker.selector = function () {
    if (!picker.el) return null;
    var selector = selectorFor(picker.el);
    return document.querySelector(selector) === picker.el ? selector : null;
  };

  window.__ttsPicker = picker;
  picker.start();
  return true;
})();
