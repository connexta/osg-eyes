## OSG-Eyes
A CLI toolkit for working with dependency data.

### Quickstart

Download the release jar. Ensure JDK 8 or higher is available on your system. Navigate to the
directory where source code repositories are cloned and start the application:
```
java -jar osg-eyes/target/osgeyes-VERSION.jar
```

If running the jar in the same directory as code repositories is not an option, the system
property `repos.home` can be specified:
```
java -Drepos.home=/path/to/cloned/repos -jar osgeyes-VERSION.jar
```

Once running, ensure all repositories have the version of the code checked out that is to be
analyzed, and that the last build ran was against the repositories in that state. It is important
the data in Maven's `/target` directories accurately reflect the data to be analyzed. Once that
is done, the first step is to index the desired repositories for analysis:
```
osgeyes=> (index-repos ddf)
1 repositories indexed:
{:manifests 433}
```

The `index-repos` command supports variadic arguments:
```
osgeyes=> (index-repos ddf alliance)
2 repositories indexed:
{:manifests 461}
```

After indexing, dependencies can be listed:
```
(list-connections)
```

Dependencies can also be graphed. This command will open up a browser to local graph output:
```
(draw-graph)
```

The default filter for both commands is the following:
```
[:node "ddf/.*" :node ".*catalog.*|.*spatial.*" :node "(?!.*plugin.*$).*"]
```

The default filter selects only nodes that:
* Belong to DDF
* Belong to Catalog or Spatial
* Excluding any plugins

A filter is just a vector of alternating keywords and regex-string values, where the regex must
match the term that the keyword points to in order for the edge to be kept in the result set. The
supported terms are:
* `:node` - name of the node to include; for now they follow the convention 
`"repo-name/Bundle-SymbolicName"` but this is subject to change.
* `:type` - general classifier string for the source of the edge, i.e. `"bundle/package"`, 
`"bundle/service"`, `"maven/compile"`, `"maven/test"`, etc.
* `:cause` - the detail, likely a package name, that is responsible for the edge.

Drawing the same graph as above, but with manual specification of the filter, looks like this:
```
(draw-graph [:node "ddf/.*" :node ".*catalog.*|.*spatial.*" :node "(?!.*plugin.*$).*"])
```

Similarly, listing connections can take a filter but also needs the max number of entries to display:
```
(list-connections 30 [:node "ddf/.*" :node ".*catalog.*|.*spatial.*" :node "(?!.*plugin.*$).*"])
```