(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [schema.core :as s]
            [buergeramt-sniper.crawler2 :as crawler])
  (:gen-class))

(s/defrecord RunParams
  [base-url :- s/Str])

(defn init [base-url]
  (log/info "Starting system...")
  (let [system (component/system-map
                 :run-params (strict-map->RunParams {:base-url base-url}))
        started-system (component/start system)]
    (log/info "System started.")
    started-system))

(defn init-debug []
  ;(init "https://service.berlin.de/dienstleistung/121482/")
  (init "https://service.berlin.de/dienstleistung/326423/")
  ;(init "https://service.berlin.de/dienstleistung/120686/")
  )

(defn run [system]
  (log/info "Running...")
  (log/spy system)
  (crawler/gather-data system)
  (log/info "Done"))

(defn -main
  [base-href & args]
  (run (init base-href)))
