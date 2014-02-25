## enable PHP in public_html http://pricklytech.wordpress.com/2011/04/02/ubuntu-server-apache-php-files-are-download-instead-of-opening-in-browser/
##
WEBFOLDER=${HOME}/public_html
MWMAJOR=1
MWMINOR=22
MWMICRO=2
MWDIR=${WEBFOLDER}/mediawiki-$(MWMAJOR).$(MWMINOR).$(MWMICRO)
MWSETTINGS=$(MWDIR)/LocalSettings.php

.PHONY=mediawiki

mediawiki:$(MWDIR)
$(MWDIR):${WEBFOLDER}/mediawiki-$(MWMAJOR).$(MWMINOR).$(MWMICRO).tar.gz
	tar xvf $< -C ${WEBFOLDER}
	mkdir -p $(MWDIR)/DATABASE
	php $(MWDIR)/maintenance/install.php --wiki localgenewiki \
		 --dbtype SQLITE \
		 --dbname localgenewiki \
		 --dbpath $(MWDIR)/DATABASE \
		 --pass adminadmin \
		--scriptpath /\~${LOGNAME}/mediawiki-$(MWMAJOR).$(MWMINOR).$(MWMICRO) \
		LocalGeneWiki WikiSysop
	chmod o+wx $(MWDIR)/DATABASE
	chmod o+rw $(MWDIR)/DATABASE/*.sqlite
	echo '$$wgAllowExternalImages=true;' >> $(MWSETTINGS)
	php $(MWDIR)/maintenance/update.php
	


${WEBFOLDER}/mediawiki-$(MWMAJOR).$(MWMINOR).$(MWMICRO).tar.gz:
	wget -O $@ http://download.wikimedia.org/mediawiki/$(MWMAJOR).$(MWMINOR)/mediawiki-$(MWMAJOR).$(MWMINOR).$(MWMICRO).tar.gz


clean:
	rm -rf $(MWDIR)

clean-all: clean
	rm -f ${WEBFOLDER}/mediawiki-$(MWMAJOR).$(MWMINOR).$(MWMICRO).tar.gz
