function toggleFolder(id, imageNode) {
  var folder = document.getElementById(id);
  if (folder === null) {
  }
  else if (folder.style.display === "block") {
    folder.style.display = "none";
  }
  else {
    folder.style.display = "block";
  }
  if (imageNode === null) {
  }
  else {
    var l = imageNode.src.length;
    if (imageNode.src.substring(l - 20, l) === "ftv2folderclosed.png") {
      // prefix = imageNode.src.substring(0, l - 20);
      imageNode.src = "@ftv2folderopen.png";
    }
    else if (imageNode.src.substring(l - 18, l) === "ftv2folderopen.png") {
      // prefix = imageNode.src.substring(0, l - 18);
      imageNode.src = "@ftv2folderclosed.png";
    }
  }

}
