(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [buergeramt-sniper.crawler :as crawler]
            [buergeramt-sniper.scraper :as scraper])
  (:gen-class))

(defn init []
  (log/info "Starting system...")
  (let [system (component/system-map
                 :crawler (crawler/map->Crawler {:base-url "https://service.berlin.de/dienstleistung/120686/"}))
        started-system (component/start system)]
    (log/info "System started.")
    started-system))

(defn run [system]
  (log/info "Running...")
  (log/spy system)
  (log/spy
    (some-> (crawler/load-root (:crawler system))
            (scraper/parse-root)))
  (log/info "OK"))

(defn -main
  [& args]
  (run (init)))
