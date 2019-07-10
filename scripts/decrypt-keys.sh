#!/bin/sh

openssl aes-256-cbc -K $encrypted_c92f00363573_key -iv $encrypted_c92f00363573_iv -in travis-deploy-key.enc -out travis-deploy-key -d;
chmod 600 travis-deploy-key;
cp travis-deploy-key ~/.ssh/id_rsa;
expect >/dev/null 2>&1 << EOF
  set timeout 10
  spawn ssh-add "${HOME}/.ssh/id_rsa"
  expect {
    "Enter passphrase for" {
      send "$ssh_pass\r"
    }
  }
  expect {
    timeout { exit 1 }
    "denied" { exit 1 }
    eof { exit 0 }
  }
EOF