var isIE6 = function () {
  var b = document.createElement('b');
  b.innerHTML = '<!--[if IE 6]><i></i><![endif]-->';
  return b.getElementsByTagName('i').length === 1
};

var registerTabOnClick = function () {
  $(".tablinks").each(function (i, node) {
    $(node).click(function (event) {
      event.stopPropagation();
      if (event.target !== node) {
        return;
      }
      var tabName = $(node).attr("data-target");
      $(".tabcontent").each(function (j, tabcontent) {
        $(tabcontent).addClass("hidden");
        if (tabcontent.id === tabName) {
          $(tabcontent).removeClass("hidden");
        }
      });

      $(".tablinks").each(function (j, tablink) {
        $(tablink).removeClass("active");
      });
      $(node).addClass("active");
    });
  });
};

var registerFolderToggle = function (root) {
  $(".folder", root).each(function (i, folder) {
    $(folder).on("click", function (event) {
      event.stopPropagation();
      if (event.target !== folder) {
        return;
      }
      if ($(folder).hasClass("on")) {
        $(folder).removeClass("on");
        if (isIE6()) {
          $(folder).children("ul").each(function (i, node) {
            $(node).addClass("hidden");
          });
        }
      } else {
        $(folder).addClass("on");
        if (isIE6()) {
          $(folder).children("ul").each(function (i, node) {
            $(node).removeClass("hidden");
          });
        }
      }
    });
  });
};

var addTopicNodes = function (ul, topics) {
  ul.innerHTML = "";
  $(topics).each(function (i, item) {
    if (!item || item.length < 2) {
      return;
    }

    var a = document.createElement("a");
    if (item[0]) {
      a.href = item[0];
      a.target = "basefrm";
    }
    a.innerHTML = item[1];

    var li = document.createElement("li");
    li.appendChild(a);

    if (item.length >= 3) {
      li.className = "folder";
      var childUl = document.createElement("ul");
      if (isIE6()) {
        childUl.className = "hidden";
      }
      addTopicNodes(childUl, item[2]);
      li.appendChild(childUl);
    }
    ul.appendChild(li);
  });
};

var loadTopicsTree = function () {
  $.ajax({
    url: "topics.json",
    dataType: "json",
    /** @namespace data.responseJSON **/
    complete: function (data, status) {
      $("#topics-loading").addClass("hidden");

      if (status === "success" && data.responseJSON) {
        var topics_tree = data.responseJSON;
        var ulRoot = $("#topics-tree")[0];
        addTopicNodes(ulRoot, topics_tree);
        registerFolderToggle(ulRoot);
        return;
      }

      // hide Topics tab
      $("#tablink-topics").removeClass("active").addClass("hidden");
      $("#Topics").addClass("hidden");

      // switch to Files tab
      $("#tablink-files").addClass("active");
      $("#Files").removeClass("hidden");
    }
  });

  $("#topics-loading").removeClass("hidden");
};

var loadFiles = function () {
  $.ajax({
    url: "files.json",
    dataType: "json",
    /** @namespace data.responseJSON **/
    complete: function (data, status) {
      $("#files-loading").addClass("hidden");

      if (status === "success" && data.responseJSON) {
        var files = data.responseJSON;
        var ulRoot = $("#files-tree")[0];
        addTopicNodes(ulRoot, files);
        registerFolderToggle(ulRoot);
      }
    }
  });

  $("#files-loading").removeClass("hidden");
};

var searchInCHM = function (query, use_regex) {
  $.ajax({
    url: "search.json",
    data: {q: query, regex: use_regex},
    dataType: "json",
    /** @namespace data.responseJSON **/
    complete: function (data, status) {
      $("#search-loading").addClass("hidden");

      if (status === "success" && data.responseJSON) {
        onSearchResultReceived(data.responseJSON);
      }
    }
  });

  $("#search-loading").removeClass("hidden");
  $("#search-results").addClass("hidden");
  $("#no-result-found").addClass("hidden");
};

var onSearchResultReceived = function (result) {
  /** @namespace result.ok **/
  if (!result.ok) {
    $("#search-results").addClass("hidden");
    $("#no-result-found").removeClass("hidden");
  } else {
    /** @namespace result.results **/
    var ol = $("#search-result-list");
    ol.empty();
    $(result.results).each(function (i, r) {
      var a = $(document.createElement("a"));
      a.attr("href", r[0]);
      a.attr("target", "basefrm");
      a.text(r[1]);
      a.onclick = resetHighlight;
      var li = $(document.createElement("li"));
      li.append(a);
      ol.append(li);
    });

    $("#search-results").removeClass("hidden");
    $("#toggle-highlight").removeClass("hidden").attr("data-toggle", "off");
    $("#no-result-found").addClass("hidden");
  }
};

var onSearch = function () {
  var queryNode = $("#query");
  if (!queryNode || !queryNode.val()) {
    return;
  }
  var query = queryNode.val();
  var use_regex = $("#regex").is(":checked");
  searchInCHM(query, use_regex);
};

var onSearchEnter = function (e) {
  if (e.keyCode === 13) {
    onSearch();
    return false;
  }
  return true;
};

var unhighlight = function () {
  /** @namespace top.basefrm **/
  if (top.basefrm) {
    // unhighlightNode($("body", top.basefrm.document));
    unhighlightNode(top.basefrm.document.getElementsByTagName("body")[0]);
  }
};

var unhighlightNode = function (node) {
  // Iterate into this nodes childNodes
  if (node.hasChildNodes) {
    var hi_cn;
    for (hi_cn = 0; hi_cn < node.childNodes.length; hi_cn++) {
      unhighlightNode(node.childNodes[hi_cn]);
    }
  }

  // And do this node itself
  if (node.nodeType === 3) { // text node
    var pn = node.parentNode;
    if (pn.className === "searchword") {
      var prevSib = pn.previousSibling;
      var nextSib = pn.nextSibling;
      nextSib.nodeValue = prevSib.nodeValue + node.nodeValue
          + nextSib.nodeValue;
      prevSib.nodeValue = '';
      node.nodeValue = '';
    }
  }
};

var highlightWord = function (doc, node, word) {
  // Iterate into this nodes childNodes
  if (node.hasChildNodes) {
    var hi_cn;
    for (hi_cn = 0; hi_cn < node.childNodes.length; hi_cn++) {
      highlightWord(doc, node.childNodes[hi_cn], word);
    }
  }

  // And do this node itself
  if (node.nodeType === 3) { // text node
    var tempNodeVal = node.nodeValue.toLowerCase();
    var tempWordVal = word.toLowerCase();
    if (tempNodeVal.indexOf(tempWordVal) !== -1) {
      var pn = node.parentNode;
      if (pn.className !== "searchword") {
        // word has not already been highlighted!
        var nv = node.nodeValue;
        var ni = tempNodeVal.indexOf(tempWordVal);
        // Create a load of replacement nodes
        var before = doc.createTextNode(nv.substr(0, ni));
        var docWordVal = nv.substr(ni, word.length);
        var after = doc.createTextNode(nv.substr(ni + word.length));
        var hiwordtext = doc.createTextNode(docWordVal);
        var hiword = doc.createElement("span");
        hiword.className = "searchword";
        hiword.style.background = "yellow";
        hiword.appendChild(hiwordtext);
        pn.insertBefore(before, node);
        pn.insertBefore(hiword, node);
        pn.insertBefore(after, node);
        pn.removeChild(node);
      }
    }
  }
};

var localSearchHighlight = function (doc, searchStr) {
  if (!doc.createElement) {
    return;
  }
  if (searchStr === '') {
    return;
  }
  window.unescape = window.unescape || window.decodeURI;
  // Trim leading and trailing spaces after unescaping
  searchStr = window.unescape(searchStr).replace(/^\s+|\s+$/g, "");
  if (searchStr === '') {
    return;
  }
  var phrases = searchStr.replace(/\+/g, ' ').split(/"/);
  // Use this next line if you would like to force the script to always
  // search for phrases. See below as well!!!
  //phrases = new Array(); phrases[0] = ''; phrases[1] = searchStr.replace(/\+/g,' ');
  for (var p = 0; p < phrases.length; p++) {
    phrases[p] = window.unescape(phrases[p]).replace(/^\s+|\s+$/g, "");
    if (phrases[p] === '') {
      continue;
    }

    var words;
    if (p % 2 === 0) {
      words = phrases[p].replace(
          /([+,()]|%(29|28)|\W+(AND|OR)\W+)/g, ' ').split(/\s+/);
    } else {
      words = new Array(1);
      words[0] = phrases[p];
    }
    for (var w = 0; w < words.length; w++) {
      if (words[w] === '') {
        continue;
      }
      highlightWord(doc, doc.getElementsByTagName("body")[0], words[w]);
    }
  }
};

var toggleHighlight = function () {
  var highlightToggle = $("#toggle-highlight");
  var status = highlightToggle.attr("data-toggle");
  if (status === "off") {
    highlightToggle.attr("data-toggle", "on");
    if (!top.basefrm) {
      return;
    }

    var query = $("#query").val();
    if (!query) {
      return;
    }

    localSearchHighlight(top.basefrm.document, query);

    var nodes = $(".searchword", top.basefrm.document);
    if (nodes && nodes.length >= 1) {
      nodes[0].scrollIntoView(true);
    }
  } else {
    highlightToggle.attr("data-toggle", "off");
    unhighlight();
  }
};

function resetHighlight() {
  var highlightToggle = $("#toggle-highlight");
  var status = highlightToggle.attr("data-toggle");
  if (status === "on") {
    highlightToggle.attr("data-toggle", "off");
  }
}

$(document).ready(function () {
  registerTabOnClick();
  loadTopicsTree();
  loadFiles();
});
