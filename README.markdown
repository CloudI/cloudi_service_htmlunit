[CloudI](https://cloudi.org) HtmlUnit Service
=============================================

[![Build Status](https://travis-ci.org/CloudI/cloudi_service_htmlunit.png)](https://travis-ci.org/CloudI/cloudi_service_htmlunit)

WHY?
----

For web scraping modern websites it is necessary to use the rendered result
after JavaScript has modified the contents.  The `cloudi_service_htmlunit`
CloudI service provides the rendered result as XML while isolating any
problems that may exist in the HtmlUnit source code.  Browser source code
is typically known for instability and HtmlUnit has had memory consumption
problems in the past.  However, `cloudi_service_htmlunit` provides reliable
HtmlUnit processing while tolerating transient HtmlUnit bugs.

BUILD
-----

Use maven and JDK >= 1.8 to build:

    mvn clean package


RUNNING
-------

Start the `cloudi_service_htmlunit` Java service:

    export JAVA=`which java`
    export PWD=`pwd`
    export USER=`whoami`
    cat << EOF > htmlunit.conf
    [[{prefix, "/browser/"},
      {file_path, "$JAVA"},
      {args, "-Dfile.encoding=UTF-8 "
             "-server "
             "-ea:org.cloudi... "
             "-Xms1g -Xmx1g "
             "-jar $PWD/target/cloudi_service_htmlunit-2.0.2-jar-with-dependencies.jar "
             "-browser default"},
      {count_thread, 4},
      {options,
       [{owner, [{user, "$USER"}]},
        {directory, "$PWD"}]}]]
    EOF
    curl -X POST -d @htmlunit.conf http://localhost:6464/cloudi/api/rpc/services_add.erl

To extract the Java quickstart from https://cloudi.org using a XPath query:

    curl -G --data-urlencode "url=https://cloudi.org" --data-urlencode "xpath=//div[@id='Java']" http://localhost:6464/browser/render

