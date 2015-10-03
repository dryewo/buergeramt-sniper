(ns buergeramt-sniper.loader
  (:require [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [net.cgrand.enlive-html :as html]
            [clojurewerkz.urly.core :as urly]
            [schema.core :as s]
            [pandect.algo.md5 :as md5]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(s/defschema Dom
  "Enlive DOM schema"
  (s/either s/Str
            {:type s/Any
             :data s/Any}
            {:tag     s/Any
             :attrs   s/Any
             :content s/Any}))

(def Href #"^http")

(s/defrecord Loader
  [use-caching :- s/Bool])

(s/defn resolve-href :- Dom
  "Given a node, resolve its :href attr if it is relative, return transformed node"
  [base-url :- Href
   node :- Dom]
  (let [resolve-fn #(try (urly/resolve base-url %)
                         (catch Exception _ %))]
    (-> node
        (update-in [:attrs :href] resolve-fn)
        (update-in [:attrs :action] resolve-fn))))

(s/defn parse-html :- [Dom]
  "Parse page body and resolve relative hrefs, return DOM"
  [url :- Href
   body :- s/Str]
  (-> (html/html-snippet body)
      (html/at [#{:a :form}] (partial resolve-href url))))

(s/defn get-cache-file-path :- s/Str
  [obj]
  (str "cache/" (md5/md5 (str obj))))

(s/defn load-from-cache
  [loader :- Loader
   key-obj]
  (when (:use-caching loader)
    (let [cache-file-path (get-cache-file-path key-obj)]
      (when (.exists (io/as-file cache-file-path))
        (log/debug "Reading from" cache-file-path)
        (with-open [in (PushbackReader. (io/reader cache-file-path))]
          (read in))))))

(s/defn save-to-cache
  [loader :- Loader
   href :- Href
   obj]
  (when (:use-caching loader)
    (let [cache-file-path (get-cache-file-path href)]
      (io/make-parents cache-file-path)
      (with-open [out (io/writer cache-file-path)]
        (binding [*print-dup* true
                  *out* out]
          (prn obj))))))

(def DEFAULT_HEADERS {"User-Agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36"
                      })

(s/defn load-page :- [Dom]
  "Load a page and parse it, return DOM or nil"
  [loader :- Loader
   href :- Href]
  (log/debug "Loading" href)
  (let [cache-key-obj href]
    (let [response (or (load-from-cache loader cache-key-obj)
                       @(http/get href {:headers DEFAULT_HEADERS}))
          {:keys [status body error]} response]
      (log/trace [status error])
      (if error
        (log/error "Error:" error)
        (if (not= 200 status)
          (log/error "Status:" status)
          (do (save-to-cache loader cache-key-obj response)
              (parse-html href body)))))))

#_(s/defn load-page-post :- [Dom]
    [loader :- Loader
     href :- s/Str
     form-params :- {s/Str s/Str}]
    (log/debug "Loading" href)
    (log/spy form-params)
    (log/debug "Loading" href)
    (if-let [cached-body (load-from-cache loader href)]
      (parse-html href cached-body)
      (let [{:keys [status body error]} @(http/post href {:headers     DEFAULT_HEADERS
                                                          :form-params form-params})]
        (log/trace [status error])
        (if error
          (log/error "Error:" error)
          (if (not= 200 status)
            (log/error "Status:" status)
            (do (save-to-cache loader href body)
                (parse-html href body)))))))
