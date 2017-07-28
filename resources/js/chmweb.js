Array.prototype.clean = function () {
  for (var i = 0; i < this.length; i++) {
    if (this[i] === undefined) {
      this.splice(i, 1);
      i--;
    }
  }
  return this;
};

if (!Function.prototype.bind) {
  Function.prototype.bind = function (oThis) {
    if (typeof this !== "function") {
      // closest thing possible to the ECMAScript 5
      // internal IsCallable function
      throw new TypeError("Function.prototype.bind - what is trying to be bound is not callable");
    }

    var aArgs = Array.prototype.slice.call(arguments, 1),
        fToBind = this,
        fNOP = function () {},
        fBound = function () {
          return fToBind.apply(this instanceof fNOP && oThis
                  ? this
                  : oThis,
              aArgs.concat(Array.prototype.slice.call(arguments)));
        };

    fNOP.prototype = this.prototype;
    fBound.prototype = new fNOP();

    return fBound;
  };
}

var openTab = function (event, tabName) {
  var i;
  var tabcontent = document.querySelectorAll(".tabcontent");
  for (i = 0; i < tabcontent.length; i++) {
    tabcontent[i].className = tabcontent[i].className.replace(" active", "");
    if (tabcontent[i].id === tabName) {
      tabcontent[i].className += " active";
    }
  }

  var tablinks = document.querySelectorAll(".tablinks");
  for (i = 0; i < tablinks.length; i++) {
    tablinks[i].className = tablinks[i].className.replace(" active", "");
  }
  var target = (event.currentTarget) ? event.currentTarget : event.srcElement;
  target.className += " active";
};

var toggleFolder = function (node, evt) {
  evt = evt || window.event;
  var folder = evt.target || evt.srcElement;
  if (folder !== node) {
    return;
  }
  if (folder.className.indexOf(" on") > 0) {
    folder.className = folder.className.replace(" on", "");
  } else {
    folder.className += " on";
  }
};

var registerFolderToggle = function (root) {
  var folders = root.querySelectorAll(".folder");
  for (var i = 0; i < folders.length; i++) {
    var folder = folders[i];
    folder.onclick = toggleFolder.bind(null, folder);
  }
};

var addTopicNodes = function (ul, topics) {
  ul.innerHTML = "";
  topics.clean();
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
};

var loadTopicsTree = function () {
  var xmlhttp = new XMLHttpRequest();
  xmlhttp.open("GET", "topics.json", true);
  xmlhttp.onreadystatechange = function () {
    if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
      onTopicsTreeReceived(xmlhttp.responseText);
    }
  };
  xmlhttp.send(null);
};

var onTopicsTreeReceived = function (text) {
  if (text) {
    // console.debug(text);
    var json = eval("(" + text + ")");
    if (json) {
      var topics_tree = json;
      var ulRoot = document.getElementById("topics-tree");
      addTopicNodes(ulRoot, topics_tree);
      registerFolderToggle(ulRoot);
      return;
    }
  }

  var tablinkTopics = document.getElementById("tablink-topics");
  var tabcontentTopics = document.getElementById("Topics");
  if (tablinkTopics) {
    tablinkTopics.className = tablinkTopics.className.replace(" active", "");
    tablinkTopics.className += " hidden";
  }
  if (tabcontentTopics) {
    tabcontentTopics.className = tabcontentTopics.className.replace(" active",
        "");
    tabcontentTopics.className += " hidden";
  }

  var tablinkFiles = document.getElementById("tablink-files");
  var tabcontentFiles = document.getElementById("Files");
  if (tablinkFiles) {
    tablinkFiles.className += " active";
  }
  if (tabcontentFiles) {
    tabcontentFiles.className += " active";
  }
};

var loadFiles = function () {
  var xmlhttp = new XMLHttpRequest();
  xmlhttp.open("GET", "files.json", true);
  xmlhttp.onreadystatechange = function () {
    if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
      onFilesTreeReceived(xmlhttp.responseText);
    }
  };
  xmlhttp.send(null);
};

var onFilesTreeReceived = function (text) {
  if (text) {
    // console.debug(text);
    var json = eval("(" + text + ")");
    if (json) {
      var files = json;
      var ulRoot = document.getElementById("files-tree");
      addTopicNodes(ulRoot, files);
      registerFolderToggle(ulRoot);
    }
  }
};

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

var onSearchResultReceived = function (text) {
  var search_results;
  var no_result_found;
  if (!text) {
    return;
  }

  // console.debug(text);

  var result = eval("(" + text + ")");
  /** @namespace result.ok **/
  if (!result.ok) {
    search_results = document.getElementById("search-results");
    if (search_results) {
      if (search_results.className.indexOf(" hidden") < 0) {
        search_results.className += " hidden";
      }
    }
    no_result_found = document.getElementById("no-result-found");
    if (no_result_found) {
      if (no_result_found) {
        no_result_found.className = no_result_found.className.replace(
            " hidden", "");
      }
    }
  } else {
    /** @namespace result.results **/
    var items = result.results;
    items.clean();
    search_results = document.getElementById("search-results");
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

    no_result_found = document.getElementById("no-result-found");
    if (no_result_found) {
      if (no_result_found.className.indexOf(" hidden") < 0) {
        no_result_found.className += " hidden";
      }
    }
  }
};

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
  if (e.keyCode === 13) {
    onSearch();
    return false;
  }
  return true;
};

var unhighlight = function () {
  /** @namespace top.basefrm **/
  if (top.basefrm) {
    unhighlightNode(top.basefrm.document.getElementsByTagName('body')[0]);
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