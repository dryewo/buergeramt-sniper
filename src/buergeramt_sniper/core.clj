(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clojure.pprint :refer [print-table]]
            [schema.core :as s]
            [clj-time.format :as tf]
            [clj-yaml.core :as yaml]
            [buergeramt-sniper.scheduler :as scheduler]
            [buergeramt-sniper.loader :as loader]
            [buergeramt-sniper.api :as api])
  (:gen-class)
  (:import (org.joda.time DateTime)))

(s/defrecord RunParams
  [base-url :- s/Str
   start-date :- (s/maybe DateTime)
   end-date :- (s/maybe DateTime)
   user-form-params :- {s/Keyword s/Str}])

(s/defschema SniperYaml
  {:Nachname                                        s/Str
   :EMail                                           s/Str
   (s/optional-key :telefonnummer_fuer_rueckfragen) s/Str
   (s/optional-key :Anmerkung)                      s/Str})

(defn read-yaml-config [file]
  (s/validate SniperYaml (yaml/parse-string (slurp file))))

(defn init
  "Configure system and start it"
  ([user-form-params base-url]
   (init user-form-params base-url nil nil))
  ([user-form-params base-url end-date]
   (init user-form-params base-url nil end-date))
  ([user-form-params base-url start-date end-date]
   (log/info "Starting system...")
   (log/spy :info user-form-params)
   (let [fmt (tf/formatters :date)
         system (component/system-map
                  :run-params (strict-map->RunParams
                                {:base-url         base-url
                                 :start-date       (some->> start-date (tf/parse fmt))
                                 :end-date         (some->> end-date (tf/parse fmt))
                                 :user-form-params user-form-params})
                  :loader (loader/strict-map->Loader {:use-caching        false
                                                      :use-local-for-post "http://localhost:8000"})
                  :scheduler (scheduler/strict-map->Scheduler {}))
         started-system (component/start system)]
     (log/info "System started.")
     started-system)))

(defn init-debug
  "Provide run-params which are normally supplied wia command-line, then run init"
  []
  (let [
        ;base-url "https://service.berlin.de/dienstleistung/121482/"
        ;base-url "https://service.berlin.de/dienstleistung/326423/"
        base-url "https://service.berlin.de/dienstleistung/120686/" ; Anmeldung einer Wohnung
        dates {
               ;:start-date "2015-10-10"
               ;:end-date "2015-10-13"
               }
        user-form-params (read-yaml-config "sniper.yaml")
        ]
    (init user-form-params base-url (:start-date dates) (:end-date dates))))

(defn run
  "Main function that is called after the system is started"
  [system]
  (log/info " Running... ")
  (log/spy system)
  (api/start-trying system))

(defn -main
  "Entry point when run from a command line"
  [base-href & [date1 date2]]
  (let [user-form-params (read-yaml-config "sniper.yaml")
        system (init user-form-params base-href date1 date2)]
    (try (run system)
         (finally (component/stop system)))))
