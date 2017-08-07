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

var registerSingleFolderToggle = function (folder) {
  /** @namespace data.responseJSON **/
  $(folder).on("click", function (event) {
    event.stopPropagation();
    if (event.target !== folder) {
      return;
    }
    if ($(folder).hasClass("on")) {
      $(folder).removeClass("on");
      if (isIE6()) {
        //noinspection JSValidateTypes
        $(folder).children("ul").each(function (i, node) {
          $(node).addClass("hidden");
        });
      }
    } else {
      $(folder).addClass("on");
      if (isIE6()) {
        //noinspection JSValidateTypes
        $(folder).children("ul").each(function (i, node) {
          $(node).removeClass("hidden");
        });
      }
    }
  });
};

var registerFolderToggle = function (root) {
  $(".folder", root).each(function (i, folder) {
    registerSingleFolderToggle(folder);
  });
};

var addTreeNodes = function (ul, treeItems) {
  ul.innerHTML = "";
  $(treeItems).each(function (i, item) {
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

    if (item.length === 3) {
      li.className = "folder";
      var childUl = document.createElement("ul");
      if (isIE6()) {
        childUl.className = "hidden";
      }
      addTreeNodes(childUl, item[2]);
      li.appendChild(childUl);
    } else if (item.length === 4) {
      var cmd = item[2];
      var treeID = item[3];
      if (cmd === "load-by-id" && treeID > 0) {
        li.className = "folder-to-load";
        li.id = "folder" + treeID;
      }
    }
    ul.appendChild(li);
  });
};

var registerLazyLoadSubtree = function (root, isTopics) {
  $(".folder-to-load", root).each(function (i, folder) {
    registerLazyLoadSingleSubtree(folder, isTopics);
  });
};

var registerLazyLoadSingleSubtree = function (folder, isTopics) {
  $(folder).on("click", function (event) {
    event.stopPropagation();
    if (event.target !== folder) {
      return;
    }
    var folderID = folder.id;
    var treeID = folderID.replace("folder", "");
    $.ajax({
      url: isTopics ? "topics.json" : "files.json",
      data: {id: treeID},
      dataType: "json",
      /** @namespace data.responseJSON **/
      complete: function (data, status) {
        if (status === "success" && data.responseJSON) {
          var topicsTreeItems = data.responseJSON;
          var childUl = document.createElement("ul");
          addTreeNodes(childUl, topicsTreeItems);
          folder.appendChild(childUl);
          registerFolderToggle(childUl);
          registerLazyLoadSubtree(childUl, isTopics);
          $(folder).removeClass("folder-to-load").addClass("folder on");
          $(folder).off("click");
          registerSingleFolderToggle(folder);
        }
      }
    });
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
        var topicsTreeItems = data.responseJSON;
        var ulRoot = $("#topics-tree")[0];
        addTreeNodes(ulRoot, topicsTreeItems);
        registerFolderToggle(ulRoot);
        registerLazyLoadSubtree(ulRoot, true);
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
        var filesTreeItems = data.responseJSON;
        var ulRoot = $("#files-tree")[0];
        addTreeNodes(ulRoot, filesTreeItems);
        registerFolderToggle(ulRoot);
        registerLazyLoadSubtree(ulRoot, false);
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
    cache: false,
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
  $("#toggle-highlight").addClass("hidden");
  $("#no-result-found").addClass("hidden");
};

var onSearchResultReceived = function (result) {
  /** @namespace result.ok **/
  if (!result.ok) {
    $("#search-results").addClass("hidden");
    $("#toggle-highlight").addClass("hidden");
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
      a.on("click", resetHighlight);
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

function loadChmInfo() {
  $.ajax({
    url: "info.json",
    dataType: "json",
    cache: false,
    /** @namespace data.responseJSON **/
    complete: function (data, status) {
      /** @namespace info.ok **/
      /** @namespace info.hasIndex **/
      /** @namespace info.buildIndexStep **/
      var info;
      if (status === "success" && data.responseJSON) {
        info = data.responseJSON;
      } else {
        info = {ok: false};
      }

      if (!info.ok) {
        return;
      }
      if (!info.hasIndex) {
        var step = info.buildIndexStep;
        updateBuildIndexStep(step);
      }
    }
  });

  // $("#build-index-panel").addClass("hidden");
}

function updateBuildIndexStep(step) {
  var $panel = $("#build-index-panel");
  if (step >= 100) {
    $panel.addClass("hidden");
    return;
  }

  $panel.removeClass("hidden");

  var $no_index_building = $(".no-index-building");
  var $index_building = $(".index-building");
  if (step < 0) {
    step = 0;
    $no_index_building.removeClass("hidden");
    $index_building.addClass("hidden");
  } else {
    $no_index_building.addClass("hidden");
    $index_building.removeClass("hidden");
  }

  var $step = $("#build-index-step")[0];
  $step.style.width = (step < 15 ? 15 : step) + '%';
  $step.innerHTML = step + '%';

  if (step < 100) {
    setTimeout(loadChmInfo, 1000);
  }
}

function startBuildIndex() {
  $.ajax({
    url: "index.json",
    dataType: "json",
    cache: false,
    /** @namespace data.responseJSON **/
    complete: function (data, status) {
      if (status === "success" && data.responseJSON) {
        var result = data.responseJSON;
        var step = result.step;
        if (step < 0) {
          step = 0;
        }
        updateBuildIndexStep(step);
      }
    }
  });
  updateBuildIndexStep(0);
}

$(document).ready(function () {
  registerTabOnClick();
  loadTopicsTree();
  loadFiles();
  loadChmInfo();
});
