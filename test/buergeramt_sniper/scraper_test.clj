(ns buergeramt-sniper.scraper-test
  (:require [clojure.test :refer :all]
            [buergeramt-sniper.scraper :refer :all]))

(deftest can-get-by-re
  (is (= "aaa"
         (get-by-re {"123" "aaa"} #"23")))
  (is (= nil
         (get-by-re {"000" "aaa"} #"23"))))
