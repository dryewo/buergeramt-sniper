(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [buergeramt-sniper.core]))

(def system
  "A Var containing an object representing the application under
  development."
  nil)

(defn start
  "Starts the system running, sets the Var #'system."
  []
  (alter-var-root #'system
                  (constantly (buergeramt-sniper.core/init))))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes and starts the system running."
  []
  (start)
  (buergeramt-sniper.core/run system))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))