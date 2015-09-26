(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defrecord Crawler [base-url])

(defn init []
  (log/info "Starting system...")
  (let [system (component/system-map
                 :crawler (Crawler. "https://service.berlin.de/dienstleistung/120686/"))
        started-system (component/start system)]
    (log/info "System started.")
    started-system))

(defn run [system]
  (log/info "Running...")
  (log/spy system)
  (log/info "OK"))

(defn -main
  [& args]
  (run (init)))
