(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [buergeramt-sniper.core]
            [schema.core :as s]
            [org.httpkit.server :as server]
            [clojure.tools.logging :as log]))

(def system
  "A Var containing an object representing the application under
  development."
  nil)

(defonce dev-server (atom nil))

(defn dev-server-fn [req]
  (log/spy (update-in req [:body] #(when % (slurp %))))
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "<html><body>YES</body></html>"})

(defn stop-dev-server []
  (when-not (nil? @dev-server)
    (@dev-server :timeout 100)
    (reset! dev-server nil)))

(defn start-dev-server []
  (reset! dev-server (server/run-server #'dev-server-fn {:port 8000})))

(defn start
  "Starts the system running, sets the Var #'system."
  []
  (start-dev-server)
  (alter-var-root #'system
                  (constantly (buergeramt-sniper.core/init-debug))))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s))))
  (stop-dev-server))

(defn go
  "Initializes and starts the system running."
  []
  (start)
  (s/with-fn-validation (buergeramt-sniper.core/run system)))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))