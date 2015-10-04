# buergeramt-sniper

A useful tool that helps to get an appointment at Berlin public services (https://service.berlin.de/dienstleistungen/)


## Installation

- Install [Leiningen](http://leiningen.org/) to be able to use Clojure.
- Clone the repo.

It is also highly recommended to use [Tor](https://www.torproject.org/docs/documentation.html.en) for IP address cycling, because otherwise there is a chance of being banned for too much requests (HTTP 429).

    $ brew install tor

or

    $ sudo apt-get install -y tor

## Usage

First go to (https://service.berlin.de/dienstleistungen/) and choose the service that you are trying to get an appointment to.
The page should have **Termin berlinweit suchen** button on the right.
Copy its URL (should look like `https://service.berlin.de/dienstleistung/120686/`).

Try to find at least one available time for this service and open the form page. Look into page source and find the names of the necessary text inputs. Usually they look like this:

```
<input type="text" class="span12" name="Nachname" id="Nachname" value="" maxlength="50" tabindex="5">
```

Usually there are 3 important inputs, namely:

- `Nachname`
- `EMail`
- either `Anmerkung` or `telefonnummer_fuer_rueckfragen`

Create a file `sniper.yaml` in the project root and put there the strings that you are going to provide to that form when the appointment will be found for you, for example:

```
Nachname: Michael Jackson
EMail: michael.jackson@example.com
telefonnummer_fuer_rueckfragen: "+4915781399988"

```

Before starting the **sniper**, run Tor in a separate terminal session:

    $ tor MaxCircuitDirtiness 60

Tor starts to listen on port 9050 by default, works as a SOCKS proxy and changes IPs every 60 seconds. After you've done, you can stop it with `Ctrl-C`.

Then finally run the **sniper** and wait for it to find an appointment for you.

    $ lein run https://service.berlin.de/dienstleistung/120686/

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
