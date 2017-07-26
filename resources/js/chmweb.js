function openTab(evt, tabName) {
  var i, tabcontent, tablinks;

  tabcontent = document.getElementsByClassName("tabcontent");
  for (i = 0; i < tabcontent.length; i++) {
    tabcontent[i].className = tabcontent[i].className.replace(" active", "");
    if (tabcontent[i].id === tabName) {
      tabcontent[i].className += " active";
    }
  }

  tablinks = document.getElementsByClassName("tablinks");
  for (i = 0; i < tablinks.length; i++) {
    tablinks[i].className = tablinks[i].className.replace(" active", "");
  }
  evt.currentTarget.className += " active";
}

var toggleFolder = function (node, e) {
  var folder = e.target;
  if (folder !== node) {
    return;
  }
  if (folder.className.indexOf(" on") > 0) {
    folder.className = folder.className.replace(" on", "");
  } else {
    folder.className += " on";
  }
};

function registerFolderToggle() {
  var folders = document.getElementsByClassName("folder");
  for (var i = 0; i < folders.length; i++) {
    var folder = folders[i];
    folder.onclick = toggleFolder.bind(null, folder);
  }
}

function addTopicNodes(ul, topics) {
  ul.innerHTML = "";
  for (var i = 0; i < topics.length; i++) {
    var item = topics[i];
    var li = document.createElement("li");
    var a = document.createElement("a");
    a.href = item[0];
    a.innerHTML = item[1];
    a.target = "basefrm";
    li.appendChild(a);
    if (item.length >= 3) {
      li.className = "folder";
      var child_ul = document.createElement("ul");
      addTopicNodes(child_ul, item[2]);
      li.appendChild(child_ul);
    }
    ul.appendChild(li);
  }
}

function loadTopicsTree() {
  var xmlhttp = new XMLHttpRequest();
  xmlhttp.open("GET", "topics.json", true);
  xmlhttp.onreadystatechange = function () {
    if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
      onTopicsTreeReceived(xmlhttp.responseText);
    }
  };
  xmlhttp.send(null);
}

function onTopicsTreeReceived(text) {

  var topics_tree = [
    ["page1.html", "Folder 1", [
      ["page1.html", "Folder 1", [
        ["page1.html", "File 1"],
        ["page1.html", "File 1"],
        ["page1.html", "File 1"],
        ["page1.html", "File 1"],]],
      ["page1.html", "File 1"],
      ["page1.html", "File 1"],
      ["page1.html", "File 1"],],],
    ["page1.html", "Folder 1", [
      ["page1.html", "File 1"],
      ["page1.html", "File 1"],
      ["page1.html", "File 1"],
      ["page1.html", "File 1"],]],];

  if (text) {
    // console.debug(text);
    var json = eval("(" + text + ")");
    if (json) {
      topics_tree = json;
    }
  }

  var ulRoot = document.getElementById("topics-tree");
  addTopicNodes(ulRoot, topics_tree);
  registerFolderToggle();
}

function loadFiles() {
  var xmlhttp = new XMLHttpRequest();
  xmlhttp.open("GET", "files.json", true);
  xmlhttp.onreadystatechange = function () {
    if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
      onFilesTreeReceived(xmlhttp.responseText);
    }
  };
  xmlhttp.send(null);
}

function onFilesTreeReceived(text) {
  var files = [
    ["dir1", "dir1", [
      ["dir/file1", "file1"],
      ["dir/file1", "file1"],
    ]],
    ["dir1", "dir1", [
      ["dir1/file1", "file1"],
      ["dir1/file1", "file1"],
    ]],
    ["file1", "file1"],
    ["file1", "file1"],
  ];

  if (text) {
    // console.debug(text);
    var json = eval("(" + text + ")");
    if (json) {
      files = json;
    }
  }

  var ulRoot = document.getElementById("files-tree");
  addTopicNodes(ulRoot, files);
  registerFolderToggle();
}

var searchInCHM = function (query, use_regex) {
  var xmlhttp = new XMLHttpRequest();
  var url = "search.json?q=" + query;
  if (use_regex) {
    url += "&regex=1";
  }
  xmlhttp.open("GET", url, true);
  xmlhttp.onreadystatechange = function () {
    if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
      onSearchResultReceived(xmlhttp.responseText);
    }
  };
  xmlhttp.send(null);
};

function onSearchResultReceived(text) {
  if (!text) {
    return;
  }

  console.debug(text);

  var result = eval("(" + text + ")");
  if (!result.ok) {
    var search_results = document.getElementById("search-results");
    if (search_results) {
      if (search_results.className.indexOf(" hidden") < 0) {
        search_results.className += " hidden";
      }
    }
    var no_result_found = document.getElementById("no-result-found");
    if (no_result_found) {
      if (no_result_found) {
        no_result_found.className = no_result_found.className.replace(
            " hidden", "");
      }
    }
  } else {
    var items = result.results;
    var search_results = document.getElementById("search-results");
    if (search_results) {
      var ol = document.getElementById("search-result-list");
      ol.innerHTML = "";
      for (var i = 0; i < items.length; i++) {
        var r = items[i];
        var li = document.createElement("li");
        var a = document.createElement("a");
        a.href = r[0];
        a.innerHTML = r[1];
        a.target = "basefrm";
        li.appendChild(a);
        ol.appendChild(li);
      }

      search_results.className = search_results.className.replace(
          " hidden", "");

      var highlight_toggle = document.getElementById("toggleHighlight");
      if (highlight_toggle) {
        highlight_toggle.setAttribute("data-toggle", "off");
      }
    }

    var no_result_found = document.getElementById("no-result-found");
    if (no_result_found) {
      if (no_result_found.className.indexOf(" hidden") < 0) {
        no_result_found.className += " hidden";
      }
    }
  }
}
var onSearch = function () {
  var queryNode = document.getElementById("query");
  if (!queryNode || !queryNode.value) {
    return;
  }
  var query = queryNode.value;
  // console.debug("query: " + query);
  var use_regex = document.getElementById("regex").checked;
  // console.debug("use_regex: " + use_regex);

  searchInCHM(query, use_regex);
};

var onSearchEnter = function (e) {
  if (e.keyCode == 13) {
    onSearch();
    return false;
  }
  return true;
};

function unhighlight() {
  if (top.basefrm) {
    unhighlightNode(top.basefrm.document.getElementsByTagName('body')[0]);
  }
}

function unhighlightNode(node) {
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
}

function highlightWord(doc, node, word) {
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
}

function localSearchHighlight(doc, searchStr) {
  if (!doc.createElement) {
    return;
  }
  if (searchStr === '') {
    return;
  }
  // Trim leading and trailing spaces after unescaping
  var searchstr = unescape(searchStr).replace(/^\s+|\s+$/g, "");
  if (searchStr === '') {
    return;
  }
  var phrases = searchStr.replace(/\+/g, ' ').split(/\"/);
  // Use this next line if you would like to force the script to always
  // search for phrases. See below as well!!!
  //phrases = new Array(); phrases[0] = ''; phrases[1] = searchStr.replace(/\+/g,' ');
  for (p = 0; p < phrases.length; p++) {
    phrases[p] = unescape(phrases[p]).replace(/^\s+|\s+$/g, "");
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
}

var toggleHighlight = function () {
  var highlight_toggle = document.getElementById("toggle-highlight");
  if (!highlight_toggle) {
    return;
  }
  var status = highlight_toggle.getAttribute("data-toggle");

  if (status === "off") {
    highlight_toggle.setAttribute("data-toggle", "on");
    if (!top.basefrm) {
      return;
    }

    var queryNode = document.getElementById("query");
    if (!queryNode || !queryNode.value) {
      return;
    }
    var query = queryNode.value;

    localSearchHighlight(top.basefrm.document, query);
  } else {
    highlight_toggle.setAttribute("data-toggle", "off");
    unhighlight();
  }
};

window.onload = function () {
  loadTopicsTree();
  loadFiles();
};