function openTab(evt, tabName) {
  let i, tabcontent, tablinks;

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

const toggleFolder = function (node, e) {
  const folder = e.target;
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
  const folders = document.getElementsByClassName("folder");
  for (let i = 0; i < folders.length; i++) {
    const folder = folders[i];
    folder.onclick = toggleFolder.bind(null, folder);
  }
}

function addTopicNodes(ul, topics) {
  ul.innerHTML = "";
  for (let i = 0; i < topics.length; i++) {
    const item = topics[i];
    const li = document.createElement("li");
    const a = document.createElement("a");
    a.href = item[0];
    a.innerHTML = item[1];
    a.target = "basefrm";
    li.appendChild(a);
    if (item.length >= 3) {
      li.className = "folder";
      const child_ul = document.createElement("ul");
      addTopicNodes(child_ul, item[2]);
      li.appendChild(child_ul);
    }
    ul.appendChild(li);
  }
}

function loadTopicsTree() {
  const xmlhttp = new XMLHttpRequest();
  xmlhttp.open("GET", "topics.json", true);
  xmlhttp.onreadystatechange = function () {
    if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
      onTopicsTreeReceived(xmlhttp.responseText);
    }
  };
  xmlhttp.send(null);
}

function onTopicsTreeReceived(text) {
  if (text) {
    // console.debug(text);
    const json = eval("(" + text + ")");
    if (json) {
      const topics_tree = json;
      const ulRoot = document.getElementById("topics-tree");
      addTopicNodes(ulRoot, topics_tree);
      registerFolderToggle();
      return;
    }
  }

  const tablinkTopics = document.getElementById("tablink-topics");
  const tabcontentTopics = document.getElementById("Topics");
  if (tablinkTopics) {
    tablinkTopics.className = tablinkTopics.className.replace(" active", "");
    tablinkTopics.className += " hidden";
  }
  if (tabcontentTopics) {
    tabcontentTopics.className = tabcontentTopics.className.replace(" active",
        "");
    tabcontentTopics.className += " hidden";
  }

  const tablinkFiles = document.getElementById("tablink-files");
  const tabcontentFiles = document.getElementById("Files");
  if (tablinkFiles) {
    tablinkFiles.className += " active";
  }
  if (tabcontentFiles) {
    tabcontentFiles.className += " active";
  }
}

function loadFiles() {
  const xmlhttp = new XMLHttpRequest();
  xmlhttp.open("GET", "files.json", true);
  xmlhttp.onreadystatechange = function () {
    if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
      onFilesTreeReceived(xmlhttp.responseText);
    }
  };
  xmlhttp.send(null);
}

function onFilesTreeReceived(text) {
  if (text) {
    // console.debug(text);
    const json = eval("(" + text + ")");
    if (json) {
      const files = json;
      const ulRoot = document.getElementById("files-tree");
      addTopicNodes(ulRoot, files);
      registerFolderToggle();

    }
  }

}

const searchInCHM = function (query, use_regex) {
  const xmlhttp = new XMLHttpRequest();
  let url = "search.json?q=" + query;
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
  let search_results;
  let no_result_found;
  if (!text) {
    return;
  }

  console.debug(text);

  const result = eval("(" + text + ")");
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
    const items = result.results;
    search_results = document.getElementById("search-results");
    if (search_results) {
      const ol = document.getElementById("search-result-list");
      ol.innerHTML = "";
      for (let i = 0; i < items.length; i++) {
        const r = items[i];
        const li = document.createElement("li");
        const a = document.createElement("a");
        a.href = r[0];
        a.innerHTML = r[1];
        a.target = "basefrm";
        li.appendChild(a);
        ol.appendChild(li);
      }

      search_results.className = search_results.className.replace(
          " hidden", "");

      const highlight_toggle = document.getElementById("toggleHighlight");
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
}
const onSearch = function () {
  let queryNode = document.getElementById("query");
  if (!queryNode || !queryNode.value) {
    return;
  }
  const query = queryNode.value;
  // console.debug("query: " + query);
  const use_regex = document.getElementById("regex").checked;
  // console.debug("use_regex: " + use_regex);

  searchInCHM(query, use_regex);
};

const onSearchEnter = function (e) {
  if (e.keyCode === 13) {
    onSearch();
    return false;
  }
  return true;
};

function unhighlight() {
  /** @namespace top.basefrm **/
  if (top.basefrm) {
    unhighlightNode(top.basefrm.document.getElementsByTagName('body')[0]);
  }
}

function unhighlightNode(node) {
  // Iterate into this nodes childNodes
  if (node.hasChildNodes) {
    let hi_cn;
    for (hi_cn = 0; hi_cn < node.childNodes.length; hi_cn++) {
      unhighlightNode(node.childNodes[hi_cn]);
    }
  }

  // And do this node itself
  if (node.nodeType === 3) { // text node
    const pn = node.parentNode;
    if (pn.className === "searchword") {
      const prevSib = pn.previousSibling;
      const nextSib = pn.nextSibling;
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
    let hi_cn;
    for (hi_cn = 0; hi_cn < node.childNodes.length; hi_cn++) {
      highlightWord(doc, node.childNodes[hi_cn], word);
    }
  }

  // And do this node itself
  if (node.nodeType === 3) { // text node
    const tempNodeVal = node.nodeValue.toLowerCase();
    const tempWordVal = word.toLowerCase();
    if (tempNodeVal.indexOf(tempWordVal) !== -1) {
      const pn = node.parentNode;
      if (pn.className !== "searchword") {
        // word has not already been highlighted!
        const nv = node.nodeValue;
        const ni = tempNodeVal.indexOf(tempWordVal);
        // Create a load of replacement nodes
        const before = doc.createTextNode(nv.substr(0, ni));
        const docWordVal = nv.substr(ni, word.length);
        const after = doc.createTextNode(nv.substr(ni + word.length));
        const hiwordtext = doc.createTextNode(docWordVal);
        const hiword = doc.createElement("span");
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
  window.unescape = window.unescape || window.decodeURI;
  // Trim leading and trailing spaces after unescaping
  searchStr = window.unescape(searchStr).replace(/^\s+|\s+$/g, "");
  if (searchStr === '') {
    return;
  }
  const phrases = searchStr.replace(/\+/g, ' ').split(/"/);
  // Use this next line if you would like to force the script to always
  // search for phrases. See below as well!!!
  //phrases = new Array(); phrases[0] = ''; phrases[1] = searchStr.replace(/\+/g,' ');
  for (let p = 0; p < phrases.length; p++) {
    phrases[p] = window.unescape(phrases[p]).replace(/^\s+|\s+$/g, "");
    if (phrases[p] === '') {
      continue;
    }

    let words;
    if (p % 2 === 0) {
      words = phrases[p].replace(
          /([+,()]|%(29|28)|\W+(AND|OR)\W+)/g, ' ').split(/\s+/);
    } else {
      words = new Array(1);
      words[0] = phrases[p];
    }
    for (let w = 0; w < words.length; w++) {
      if (words[w] === '') {
        continue;
      }
      highlightWord(doc, doc.getElementsByTagName("body")[0], words[w]);
    }
  }
}

const toggleHighlight = function () {
  let highlight_toggle = document.getElementById("toggle-highlight");
  if (!highlight_toggle) {
    return;
  }
  const status = highlight_toggle.getAttribute("data-toggle");

  if (status === "off") {
    highlight_toggle.setAttribute("data-toggle", "on");
    if (!top.basefrm) {
      return;
    }

    let queryNode = document.getElementById("query");
    if (!queryNode || !queryNode.value) {
      return;
    }
    const query = queryNode.value;

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