(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clojure.pprint :refer [print-table]]
            [schema.core :as s]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [buergeramt-sniper.crawler :as crawler]
            [buergeramt-sniper.scheduler :as scheduler]
            [clj-time.format :as tf]
            [buergeramt-sniper.loader :as loader])
  (:gen-class)
  (:import (org.joda.time DateTime)
           (buergeramt_sniper.crawler BookingResult AvailableTime)
           (buergeramt_sniper.scraper BookingSuccessPage RootPage)))

(s/defrecord RunParams
  [base-url :- s/Str
   start-date :- (s/maybe DateTime)
   end-date :- (s/maybe DateTime)
   user-form-params :- {s/Str s/Str}])

(defn init
  ([base-url]
   (init base-url nil nil))
  ([base-url end-date]
   (init base-url nil end-date))
  ([base-url start-date end-date]
   (log/info "Starting system...")
   (let [fmt (tf/formatters :date)
         system (component/system-map
                  :run-params (strict-map->RunParams
                                {:base-url         base-url
                                 :start-date       (some->> start-date (tf/parse fmt))
                                 :end-date         (some->> end-date (tf/parse fmt))
                                 :user-form-params {"Nachname"                       "Foo Bar"
                                                    "EMail"                          "foo@bar.com"
                                                    "telefonnummer_fuer_rueckfragen" "+4915711111111"}})
                  :loader (loader/strict-map->Loader {:use-caching        false
                                                      :use-local-for-post "http://localhost:8000"})
                  :scheduler (scheduler/strict-map->Scheduler {}))
         started-system (component/start system)]
     (log/info "System started.")
     started-system)))

(defn init-debug []
  (init "https://service.berlin.de/dienstleistung/121482/")
  ;(init "https://service.berlin.de/dienstleistung/326423/" "2015-10-02")
  ;(init "https://service.berlin.de/dienstleistung/120686/")
  )

(defn rand-int-between [low high]
  (+ low (rand-int (- high low))))

(s/defn pretty-print-available-times
  [available-times :- [AvailableTime]]
  (when (seq available-times)
    (log/info
      (with-out-str
        (print-table [:n :date :time :place]
                     (take 3 (map-indexed #(assoc %2 :n (inc %1)) available-times)))))))

(s/defn do-book [system] :- (s/maybe BookingResult)
  (when-let [available-times (seq (crawler/gather-available-times system))]
    (pretty-print-available-times available-times)
    (crawler/book-appointment system (:href (first available-times)))))

(s/defn pretty-print-booking-success
  [{:keys [info-items]} :- BookingSuccessPage]
  (log/info
    (with-out-str
      (println "\n")
      (println "Successully booked:")
      (println "-------------------")
      (doseq [[k v] info-items]
        (println k v)))))

(defn try-book-until-success
  "Tries to find earliest available time and book it.
  In case of failure schedules a retry after a few seconds."
  [system]
  (let [result (do-book system)]
    (if (:success result)
      (pretty-print-booking-success (:booking-page result))
      (let [next-try-time (-> (rand-int-between 10 20) t/secs t/from-now)
            error (-> result :booking-page :error)]
        (if error
          (log/info "Failed because of:" error)
          (log/info "No available time slots found."))
        (log/debug "Trying again at" (tf/unparse (tf/formatters :time) next-try-time))
        (scheduler/set-close-fn (:scheduler system)
                                (chime-at [next-try-time]
                                          (fn [_] (try-book-until-success system))))))))

(defn run [system]
  (log/info " Running... ")
  (log/spy system)
  (try-book-until-success system))

(defn -main
  [base-href & [start-date end-date]]
  (let [system (init base-href start-date end-date)]
    (try (run system)
         (finally (component/stop system)))))
