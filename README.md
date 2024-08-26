# SitemapHostNote


This extension was born from a feature request on Twitter https://twitter.com/Jhaddix/status/1827550041818505634 from Jhaddix where they wanted the ability to add a note next to hosts within the Site map. 

## How it works

The extension adds a custom context-menu when right-clicking on a host in the sitemap which contains an "Add Host Note" menu option. When clicked, the user is prompted for a note string which is added after the target hostname in paranthesis.

## How it works under the hood

     At a high level this does the following:
     1. walks the Site map JTree's children MutableTreeNodes.
     2. calls a no-arg method on the custom MutableTreeNodes object which returns the custom UserObject class containing the details of the site-map host.
     3. within that custom UserObject class we check if its superclass has a private String field.
     4. If the String field matches the hostname of the Site map node the user selected, the user is prompted for the note they want to add and the String field is updated.

### demo

![example](https://github.com/user-attachments/assets/e0437636-7aff-42fd-84e9-def90035194f)
