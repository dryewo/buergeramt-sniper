(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [schema.core :as s]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [buergeramt-sniper.crawler :as crawler]
            [buergeramt-sniper.scheduler :as scheduler]
            [clj-time.format :as tf])
  (:gen-class)
  (:import (org.joda.time DateTime)))

(s/defrecord RunParams
  [base-url :- s/Str
   start-date :- (s/maybe DateTime)
   end-date :- (s/maybe DateTime)])

(defn init
  ([base-url]
   (init base-url nil nil))
  ([base-url end-date]
   (init base-url nil end-date))
  ([base-url start-date end-date]
   (log/info "Starting system...")
   (let [fmt (tf/formatters :date)
         system (component/system-map
                  :run-params (strict-map->RunParams {:base-url   base-url
                                                      :start-date (some->> start-date (tf/parse fmt))
                                                      :end-date   (some->> end-date (tf/parse fmt))})
                  :scheduler (scheduler/strict-map->Scheduler {}))
         started-system (component/start system)]
     (log/info "System started.")
     started-system)))

(defn init-debug []
  (init "https://service.berlin.de/dienstleistung/121482/")
  ;(init "https://service.berlin.de/dienstleistung/326423/" "2015-10-02")
  ;(init "https://service.berlin.de/dienstleistung/120686/")
  )

(defn my-rand-int [low high]
  (+ low (rand-int (- high low))))

(defn random-intervals
  ([low high]
   (random-intervals low high (t/now)))
  ([low high from-time]
   (cons from-time (lazy-seq
                     (random-intervals low high
                                       (->> (my-rand-int low high)
                                            t/secs
                                            (t/plus from-time)))))))

(defn run [system]
  (log/info "Running...")
  (log/spy system)
  (crawler/get-appointment-page "https://service.berlin.de/terminvereinbarung/termin/eintragen.php?buergerID=&buergername=webreservierung&OID=52740&OIDListe=53848,53850,52740,53857,60523,22646,22647,58921,58924,58927,58930,54035,60517,22619,20497,34216,20722&datum=2015-10-30&zeit=10:40:00&behoerde=&slots=&anliegen%5B%5D=121482&dienstleister%5B%5D=121364&dienstleister%5B%5D=121362&herkunft=http://service.berlin.de/dienstleistung/121482/")
  #_(let [available-times (crawler/gather-data system)]
      (log/spy (first available-times)))
  #_(scheduler/add-close-fn (:scheduler system)
                            (chime-at (random-intervals 10 20)
                                      #(crawler/gather-data system))))

(defn -main
  [base-href & [start-date end-date]]
  (let [system (init base-href start-date end-date)]
    (try (run system)
         (finally (component/stop system)))))
