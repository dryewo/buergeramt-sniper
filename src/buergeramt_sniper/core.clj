(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clojurewerkz.urly.core :as urly]
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

(defn collect-open-dates [{:keys [crawler] :as system}]
  (when-let [initial-href (some-> (crawler/load-root crawler)
                                  (scraper/parse-root-page)
                                  :appointment-href)]
    (let [href initial-href
          visited-hrefs #{}
          results {}]
      (let [page-res (log/spy (some-> href crawler/load-calendar-page scraper/parse-calendar-page))
            new-hrefs (->> page-res
                           :months
                           (map (juxt :prev-href :next-href))
                           flatten
                           (remove nil?)
                           (map (partial urly/resolve href))
                           (into #{}))]
        (log/spy new-hrefs)))))

(defn run [system]
  (log/info "Running...")
  (log/spy system)
  (collect-open-dates system)
  (log/info "Done"))

(defn -main
  [& args]
  (run (init)))
