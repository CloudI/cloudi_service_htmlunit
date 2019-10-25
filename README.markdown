[CloudI](https://cloudi.org) HtmlUnit Service
=============================================

[![Build Status](https://travis-ci.org/CloudI/cloudi_service_htmlunit.png)](https://travis-ci.org/CloudI/cloudi_service_htmlunit)

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
      {args, "-server "
             "-ea:org.cloudi... "
             "-Xms1g -Xmx1g "
             "-jar $PWD/target/cloudi_service_htmlunit-1.8.0-jar-with-dependencies.jar "
             "-browser default"},
      {count_thread, 4},
      {options,
       [{owner, [{user, "$USER"}]},
        {directory, "$PWD"}]}]]
    EOF
    curl -X POST -d @htmlunit.conf http://localhost:6464/cloudi/api/rpc/services_add.erl

To extract the Java quickstart from https://cloudi.org using a XPath query:

    curl -G --data-urlencode "url=https://cloudi.org" --data-urlencode "xpath=//div[@id='Java']" http://localhost:6464/browser/render

