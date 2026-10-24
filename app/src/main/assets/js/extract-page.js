/*
 * Extracts the chapter text and next/prev link candidates from the current page.
 * Concatenated after Readability.js inside an IIFE by ReadabilityExtractor, so nothing
 * leaks into the page's globals. Returns a JSON string (see RawPage.kt).
 *
 * args: { selector: string|null }
 */
function ttsExtractPage(args) {
  var BLOCK_TAGS = /^(P|DIV|SECTION|ARTICLE|MAIN|HEADER|FOOTER|ASIDE|BLOCKQUOTE|PRE|UL|OL|LI|DL|DT|DD|TABLE|TR|H1|H2|H3|H4|H5|H6|HR|FIGURE|FIGCAPTION)$/;
  var SKIP_TAGS = /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE|IFRAME|OBJECT|EMBED|SVG|CANVAS|FORM|BUTTON|SELECT|TEXTAREA|INPUT|INS|NAV)$/;
  var MAX_ANCHORS = 400;

  function clip(value, max) {
    value = (value || "").replace(/\s+/g, " ").trim();
    return value.length > max ? value.slice(0, max) : value;
  }

  // Converts a DOM subtree to text: block elements and <br> become line breaks.
  function toText(root) {
    var out = [];
    (function walk(node) {
      if (node.nodeType === 3) {
        out.push(node.nodeValue);
        return;
      }
      if (node.nodeType !== 1 && node.nodeType !== 9 && node.nodeType !== 11) return;
      var tag = node.nodeName.toUpperCase();
      if (SKIP_TAGS.test(tag)) return;
      if (tag === "BR") {
        out.push("\n");
        return;
      }
      var block = BLOCK_TAGS.test(tag);
      if (block) out.push("\n");
      for (var child = node.firstChild; child; child = child.nextSibling) walk(child);
      if (block) out.push("\n");
    })(root);
    return out.join("");
  }

  function firstHeading() {
    var h1 = document.querySelector("h1");
    return h1 ? clip(h1.textContent, 300) : "";
  }

  // "Chương 5: X - Tên truyện - Site" -> "Chương 5: X" when the page heading is part of the title.
  function pickTitle(candidate) {
    var heading = firstHeading();
    candidate = clip(candidate, 300);
    if (heading && (!candidate || candidate.indexOf(heading) >= 0)) return heading;
    return candidate || heading || clip(document.title, 300);
  }

  function fromSelector(selector) {
    var el;
    try {
      el = document.querySelector(selector);
    } catch (e) {
      return null;
    }
    if (!el) return null;
    return { title: pickTitle(document.title), text: toText(el.cloneNode(true)) };
  }

  function fromReadability() {
    var article = new Readability(document.cloneNode(true)).parse();
    if (!article || !article.content) return null;
    // DOMParser gives an inert document: no scripts or event handlers run.
    var doc = new DOMParser().parseFromString(article.content, "text/html");
    return { title: pickTitle(article.title), text: toText(doc.body) };
  }

  function iconClasses(a) {
    var parts = [];
    var icons = a.querySelectorAll("i, svg, span, img");
    for (var i = 0; i < icons.length && i < 5; i++) {
      parts.push(icons[i].getAttribute("class") || "");
      if (icons[i].nodeName.toUpperCase() === "IMG") parts.push(icons[i].getAttribute("alt") || "");
    }
    return clip(parts.join(" "), 200);
  }

  function collectLinks() {
    var relNext = document.querySelector("link[rel~='next' i], a[rel~='next' i]");
    var relPrev = document.querySelector("link[rel~='prev' i], a[rel~='prev' i], link[rel~='previous' i], a[rel~='previous' i]");
    var anchors = [];
    var all = document.querySelectorAll("a[href]");
    for (var i = 0; i < all.length && anchors.length < MAX_ANCHORS; i++) {
      var a = all[i];
      var cls = a.getAttribute("class") || "";
      anchors.push({
        href: a.href,
        text: clip(a.textContent, 80),
        label: clip((a.getAttribute("aria-label") || "") + " " + (a.getAttribute("title") || ""), 120),
        attrs: clip((a.id || "") + " " + cls + " " + (a.getAttribute("rel") || ""), 200),
        icons: iconClasses(a),
        disabled: /(^|\s)disabled(\s|$)/.test(cls) || a.getAttribute("aria-disabled") === "true",
      });
    }
    return {
      relNext: relNext ? relNext.href : null,
      relPrev: relPrev ? relPrev.href : null,
      anchors: anchors,
    };
  }

  try {
    var source = "readability";
    var selectorMissed = false;
    var content = null;
    if (args.selector) {
      content = fromSelector(args.selector);
      if (content) source = "selector";
      else selectorMissed = true;
    }
    if (!content) content = fromReadability();
    return JSON.stringify({
      ok: !!content,
      url: location.href,
      title: content ? content.title : clip(document.title, 300),
      text: content ? content.text : "",
      source: source,
      selectorMissed: selectorMissed,
      links: collectLinks(),
    });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e && e.message ? e.message : e) });
  }
}
