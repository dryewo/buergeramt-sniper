(defproject buergeramt-sniper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [clj-http "2.0.0"]
                 [com.stuartsierra/component "0.3.0"]
                 [enlive "1.1.6"]
                 [clojurewerkz/urly "1.0.0"]
                 [prismatic/schema "1.0.1"]
                 [jarohen/chime "0.1.6"]
                 [pandect "0.5.4"]
                 [clj-yaml "0.4.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]]
  :main ^:skip-aot buergeramt-sniper.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :repl    {:repl-options {:init-ns user}
                       :source-paths ["repl"]
                       :dependencies [[org.clojure/tools.namespace "0.2.10"]
                                      [org.clojure/java.classpath "0.2.2"]]}})
