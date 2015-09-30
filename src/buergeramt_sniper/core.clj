(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [schema.core :as s]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [buergeramt-sniper.crawler :as crawler]
            [buergeramt-sniper.scheduler :as scheduler])
  (:gen-class))

(s/defrecord RunParams
  [base-url :- s/Str])

(defn init [base-url]
  (log/info "Starting system...")
  (let [system (component/system-map
                 :run-params (strict-map->RunParams {:base-url base-url})
                 :scheduler (scheduler/strict-map->Scheduler {}))
        started-system (component/start system)]
    (log/info "System started.")
    started-system))

(defn init-debug []
  ;(init "https://service.berlin.de/dienstleistung/121482/")
  ;(init "https://service.berlin.de/dienstleistung/326423/")
  (init "https://service.berlin.de/dienstleistung/120686/")
  )

(defn run [system]
  (log/info "Running...")
  (log/spy system)
  (let [run-fn (fn [& _] (crawler/gather-data system))]
    (run-fn)
    (scheduler/add-close-fn (:scheduler system)
                            (chime-at (periodic-seq (t/now) (-> 10 t/secs))
                                      run-fn))))

(defn -main
  [base-href & args]
  (let [system (init base-href)]
    (try (run system)
         (finally (component/stop system)))))
